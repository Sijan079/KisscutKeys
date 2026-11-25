package app.kisscut.keys.shared.core.words

import kotlin.math.abs
import kotlin.math.min

/**
 * Suggestions and the autocorrect decision for the word being typed (docs/KEYBOARD.md, Suggestions and
 * autocorrect). Works over several [lists] at once (English now, Tagalog later), with no language switch.
 * Pure and stateless: the keyboard passes in what was typed and what the user reverted.
 */
class Speller(lists: List<WordList>) {
    private class Entry(val word: Word, val key: String) {
        val mask = letterMask(key)
    }

    private val entries: List<Entry>
    /** Entries by key length and first letter: `length * 128 + first char` (letters a to z only). */
    private val byLengthAndFirst: Map<Int, List<Entry>>
    private val sortedKeys: List<Entry>
    private val byLower: Map<String, List<Word>>
    private val fixes: Map<String, String>

    init {
        val seen = HashSet<String>()
        entries = lists.flatMap { it.words }
            .filter { seen.add(it.text) }
            .map { Entry(it, key(it.text)) }
            .filter { it.key.isNotEmpty() }
        byLengthAndFirst = entries.filter { it.key[0] in 'a'..'z' }.groupBy { bucket(it.key.length, it.key[0]) }
        sortedKeys = entries.sortedBy { it.key }
        byLower = entries.groupBy({ it.word.text.lowercase() }, { it.word })
        fixes = lists.fold(emptyMap()) { all, list -> list.fixes + all }
    }

    /**
     * Up to [limit] words for [typed], best first, in the typed word's case. A known word comes first as typed;
     * then a fix pair ("cant" to "can't"), corrections (one or two typos) and completions ("tomo" to "tomorrow").
     * Offensive words are never offered.
     */
    fun suggest(typed: String, limit: Int = 3): List<String> {
        val k = key(typed)
        if (k.isEmpty() || !looksLikeAWord(typed)) return emptyList()
        val scored = HashMap<String, Double>()
        fun offer(word: Word, score: Double) {
            if (word.offensive) return
            val prev = scored[word.text]
            if (prev == null || score > prev) scored[word.text] = score
        }
        fixes[typed.lowercase()]?.let { fix -> scored[fix] = FIX_SCORE }
        for ((entry, cost) in corrections(k, MAX_SUGGEST_COST)) {
            offer(entry.word, entry.word.frequency - COST_WEIGHT * cost)
        }
        for (entry in completions(k)) {
            val extra = entry.key.length - k.length
            offer(entry.word, entry.word.frequency - COMPLETION_BASE - COMPLETION_PER_CHAR * extra)
        }
        val known = knownAsTyped(typed)
        val ranked = scored.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { withCase(typed, it.key) }
        val first = if (known != null) listOf(typed) else emptyList()
        return (first + ranked).distinct().take(limit)
    }

    /**
     * The correction to apply when [typed] is finished (space or punctuation), or null to leave it alone.
     * Never touches: known words, words with digits or symbols, ALL CAPS or MixedCase, anything in [reverted]
     * (keys from [key]), or offensive targets. Applies a fix pair only when [typed] is not a real word. Otherwise
     * only a one-typo correction with a clear lead; apostrophe and case-only fixes ("dont", "i") always apply.
     */
    fun autocorrect(typed: String, reverted: Set<String> = emptySet()): String? {
        val k = key(typed)
        if (k.isEmpty() || !looksLikeAWord(typed) || k in reverted) return null
        if (typed.length > 1 && (isAllCaps(typed) || isMixedCase(typed))) return null
        if (knownAsTyped(typed) != null || k in CHAT_WORDS) return null
        fixes[typed.lowercase()]?.let { return withCase(typed, it) }
        val candidates = corrections(k, MAX_AUTOCORRECT_COST)
            .filterNot { (e, _) -> e.word.offensive }
            .map { (e, cost) -> Triple(e, cost, e.word.frequency - COST_WEIGHT * cost) }
            .sortedWith(compareByDescending<Triple<Entry, Double, Double>> { it.third }.thenBy { it.first.word.text })
        val best = candidates.firstOrNull() ?: return null
        val (entry, cost, score) = best
        if (cost > 0.0) {
            if (k.length < MIN_CORRECT_LENGTH || entry.word.frequency < MIN_TARGET_FREQUENCY) return null
            val runnerUp = candidates.getOrNull(1)?.third
            if (runnerUp != null && score - runnerUp < MIN_LEAD) return null
        }
        return withCase(typed, entry.word.text)
    }

    /** A known word matching [typed]: same letters ignoring case, except "i" is not the acronym-like "I". */
    private fun knownAsTyped(typed: String): Word? = byLower[typed.lowercase()]?.firstOrNull { w ->
        typed == w.text || !(isAllCaps(w.text) && typed.none { it.isUpperCase() })
    }

    private fun completions(k: String): List<Entry> {
        if (k.length < MIN_COMPLETION_PREFIX) return emptyList()
        var lo = 0
        var hi = sortedKeys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sortedKeys[mid].key < k) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<Entry>()
        var i = lo
        while (i < sortedKeys.size && sortedKeys[i].key.startsWith(k)) {
            if (sortedKeys[i].key.length > k.length) out += sortedKeys[i]
            i++
        }
        return out.sortedByDescending { it.word.frequency }.take(COMPLETIONS_KEPT)
    }

    /**
     * Words within [maxCost] of [k], each with its cheapest cost. Cheap filters first, so a keystroke checks a few
     * thousand words, not fifty thousand: the first letter is the typed one, a neighbouring key, or the first two
     * are swapped; and the letters used differ by no more than the allowed edits can change.
     */
    private fun corrections(k: String, maxCost: Double): List<Pair<Entry, Double>> {
        val out = ArrayList<Pair<Entry, Double>>()
        val first = k[0]
        if (first !in 'a'..'z') return out
        val limit = tenths(maxCost)
        val span = maxCost.toInt()
        val mask = letterMask(k)
        val maxMaskBits = 2 * (limit / NEIGHBOUR_TENTHS)
        val second = k.getOrNull(1)?.takeIf { it in 'a'..'z' }
        val starts = (NEIGHBOURS[first - 'a'] + first + listOfNotNull(second)).distinct()
        val rows = Array(3) { IntArray(k.length + span + 2) }
        for (len in (k.length - span).coerceAtLeast(1)..(k.length + span)) {
            for (start in starts) {
                // A word starting with the typed second letter only counts as a swap ("hte" for "the").
                val swapOnly = start == second && start != first && !areNeighbours(start, first)
                for (e in byLengthAndFirst[bucket(len, start)] ?: continue) {
                    if (swapOnly && e.key.getOrNull(1) != first) continue
                    if ((mask xor e.mask).countOneBits() > maxMaskBits) continue
                    val cost = typoTenths(k, e.key, limit, rows)
                    if (cost <= limit) out += e to cost / 10.0
                }
            }
        }
        return out
    }

    companion object {
        /** Matching key: lower case, apostrophes dropped, so "dont" meets "don't" and "Im" meets "I'm". */
        fun key(text: String): String = buildString(text.length) {
            for (c in text) if (c != '\'' && c != '’') append(c.lowercaseChar())
        }

        /**
         * Weighted typo distance (restricted Damerau-Levenshtein) between two keys, or more than [limit] once it
         * can't get under it. A neighbouring key costs [NEIGHBOUR_COST], a swap of two letters [SWAP_COST], a missed
         * or extra double letter ("helo", "helllo") [DOUBLE_COST], any other change 1.
         */
        fun typoCost(a: String, b: String, limit: Double = 1_000.0): Double =
            typoTenths(a, b, tenths(limit), Array(3) { IntArray(b.length + 1) }) / 10.0

        /**
         * [typoCost] in tenths, in whole numbers (cheaper on a phone), with three scratch rows of at least
         * `b.length + 1` reused across calls. Returns more than [limit] once the cost can't get under it.
         */
        private fun typoTenths(a: String, b: String, limit: Int, rows: Array<IntArray>): Int {
            if (abs(a.length - b.length) * DOUBLE_TENTHS > limit) return limit + 1
            var prev2 = rows[0]
            var prev = rows[1]
            var cur = rows[2]
            prev[0] = 0
            for (j in 1..b.length) prev[j] = prev[j - 1] + if (j > 1 && b[j - 1] == b[j - 2]) DOUBLE_TENTHS else 10
            for (i in 1..a.length) {
                cur[0] = prev[0] + if (i > 1 && a[i - 1] == a[i - 2]) DOUBLE_TENTHS else 10
                var rowMin = cur[0]
                for (j in 1..b.length) {
                    val x = a[i - 1]
                    val y = b[j - 1]
                    val sub = if (x == y) 0 else if (areNeighbours(x, y)) NEIGHBOUR_TENTHS else 10
                    // Deleting a typed letter that repeats the one before it, or adding a letter the word doubles.
                    val del = if (i > 1 && x == a[i - 2]) DOUBLE_TENTHS else 10
                    val ins = if (j > 1 && y == b[j - 2]) DOUBLE_TENTHS else 10
                    var v = min(min(prev[j] + del, cur[j - 1] + ins), prev[j - 1] + sub)
                    if (i > 1 && j > 1 && x == b[j - 2] && a[i - 2] == y && x != y) {
                        v = min(v, prev2[j - 2] + SWAP_TENTHS)
                    }
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > limit) return limit + 1
                val t = prev2
                prev2 = prev
                prev = cur
                cur = t
            }
            return prev[b.length]
        }

        /** One bit per letter a to z that [key] uses. */
        private fun letterMask(key: String): Int {
            var m = 0
            for (c in key) if (c in 'a'..'z') m = m or (1 shl (c - 'a'))
            return m
        }

        private fun bucket(length: Int, first: Char) = length * 128 + first.code

        private fun tenths(cost: Double): Int = (cost * 10 + 0.5).toInt()

        /** Touching keys on the QWERTY letters, a to z by a to z (same row next door, or the rows above and below). */
        private val NEIGHBOUR_TABLE = BooleanArray(26 * 26).also { table ->
            val positions = HashMap<Char, Pair<Double, Int>>()
            listOf("qwertyuiop" to 0.0, "asdfghjkl" to 0.5, "zxcvbnm" to 1.5).forEachIndexed { row, (letters, offset) ->
                letters.forEachIndexed { i, c -> positions[c] = (i + offset) to row }
            }
            for ((x, px) in positions) for ((y, py) in positions) {
                if (x != y && abs(px.second - py.second) <= 1 && abs(px.first - py.first) <= 1.0) {
                    table[(x - 'a') * 26 + (y - 'a')] = true
                }
            }
        }

        /** For each letter a to z, the letters on touching keys. */
        private val NEIGHBOURS: List<List<Char>> = List(26) { x ->
            (0 until 26).filter { y -> NEIGHBOUR_TABLE[x * 26 + y] }.map { 'a' + it }
        }

        /** Touching keys on the QWERTY letters (same row next door, or the row above or below overlapping). */
        fun areNeighbours(x: Char, y: Char): Boolean =
            x in 'a'..'z' && y in 'a'..'z' && NEIGHBOUR_TABLE[(x - 'a') * 26 + (y - 'a')]

        private fun looksLikeAWord(text: String): Boolean =
            text.all { it.isLetter() || it == '\'' || it == '’' || it == '-' } && text.any { it.isLetter() }

        private fun isAllCaps(text: String): Boolean = text.any { it.isLetter() } && text.none { it.isLowerCase() }

        private fun isMixedCase(text: String): Boolean =
            text.drop(1).any { it.isUpperCase() } && text.any { it.isLowerCase() }

        /** "Teh" gives "The", "TEH" gives "THE"; otherwise the list's own spelling ("i'm" gives "I'm"). */
        fun withCase(typed: String, word: String): String = when {
            typed.length > 1 && isAllCaps(typed) -> word.uppercase()
            typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }

        /**
         * Chat shorthand the word list lacks; never "corrected" ("thx" is not a typo for "the"). Kept short: real
         * words in any bundled list are known anyway.
         */
        val CHAT_WORDS = setOf(
            "lol", "lmao", "lmfao", "rofl", "omg", "omfg", "idk", "idc", "brb", "btw", "bff", "thx", "thnx", "ty",
            "tysm", "np", "pls", "plz", "ppl", "rn", "ngl", "tbh", "imo", "imho", "irl", "smh", "fyi", "asap", "jk",
            "nvm", "gtg", "ttyl", "dm", "dms", "haha", "hahaha", "hehe", "hihi", "xoxo", "ikr", "wyd", "hbu", "lmk",
            "fr", "srsly", "tho", "ur", "u", "k", "kk", "ok", "okk", "yass", "yay", "aww", "ugh", "hmm", "omw",
        )

        const val NEIGHBOUR_COST = 0.6
        const val SWAP_COST = 0.6
        const val DOUBLE_COST = 0.4
        private const val NEIGHBOUR_TENTHS = 6
        private const val SWAP_TENTHS = 6
        private const val DOUBLE_TENTHS = 4
        private const val COST_WEIGHT = 100.0
        private const val MAX_SUGGEST_COST = 2.0
        private const val MAX_AUTOCORRECT_COST = 1.0
        private const val FIX_SCORE = 1_000.0
        private const val COMPLETION_BASE = 20.0
        private const val COMPLETION_PER_CHAR = 6.0
        private const val COMPLETIONS_KEPT = 20
        private const val MIN_COMPLETION_PREFIX = 2
        private const val MIN_CORRECT_LENGTH = 3
        private const val MIN_TARGET_FREQUENCY = 60
        private const val MIN_LEAD = 25.0
    }
}
