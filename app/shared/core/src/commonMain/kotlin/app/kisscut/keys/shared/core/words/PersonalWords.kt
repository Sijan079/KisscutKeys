package app.kisscut.keys.shared.core.words

/**
 * One word the user typed that the bundled lists lack. [count] is how many times it was typed; from
 * [PersonalWords.LEARN_COUNT] on it is learned. [lastSeen] is in epoch milliseconds.
 */
data class LearnedWord(val text: String, val count: Int, val lastSeen: Long)

/**
 * The user's personal word list (docs/KEYBOARD.md, Learned words): names, slang and Taglish the bundled lists lack.
 * Immutable; each change returns a new list. Pure: the keyboard decides which words reach it (never from password
 * or incognito fields) and the caller stores [entries] on the phone only.
 *
 * - A word typed [LEARN_COUNT] times, and left alone by autocorrect, is learned. So is a word whose autocorrection
 *   the user undid, at once.
 * - Words with digits or symbols, single letters and very long words are never kept.
 * - A word seen fewer than [LEARN_COUNT] times is dropped [PENDING_DAYS] days after it was last seen.
 * - At most [MAX_LEARNED] learned and [MAX_PENDING] not-yet-learned words; the least recently seen go first.
 * - Spelling: once the word is typed in lower case it stays lower case (a name typed "Juan" stays "Juan", but
 *   "Kain" at a sentence start becomes "kain" once typed mid-sentence); the speller fixes case as typed.
 */
class PersonalWords private constructor(private val byKey: Map<String, LearnedWord>) {
    /** Every kept word, learned or not, for storing. */
    val entries: Collection<LearnedWord> get() = byKey.values

    /** The learned words, for [Speller.withLearned]. */
    val known: List<String> get() = byKey.values.filter { it.count >= LEARN_COUNT }.map { it.text }

    fun isLearned(word: String): Boolean = (byKey[keyOf(word)]?.count ?: 0) >= LEARN_COUNT

    /**
     * [word] was typed and kept as typed (not changed by autocorrect), at [now]. Pass only words the bundled
     * lists lack ([Speller.isKnown] false before learning).
     */
    fun typed(word: String, now: Long): PersonalWords = add(word, now, learnNow = false)

    /** The user undid the autocorrection of [word] at [now]: they meant it, so it is learned at once. */
    fun undidAutocorrect(word: String, now: Long): PersonalWords = add(word, now, learnNow = true)

    /** This list without words that expired by [now]: for loading a stored list. */
    fun pruned(now: Long): PersonalWords = PersonalWords(trim(byKey, now))

    private fun add(word: String, now: Long, learnNow: Boolean): PersonalWords {
        if (!isKeepable(word)) return this
        val key = keyOf(word)
        val old = byKey[key]
        val text = when {
            old == null -> word
            word == word.lowercase() -> word
            else -> old.text
        }
        val count = if (learnNow) maxOf(LEARN_COUNT, (old?.count ?: 0) + 1) else (old?.count ?: 0) + 1
        return PersonalWords(trim(byKey + (key to LearnedWord(text, count, now)), now))
    }

    companion object {
        const val LEARN_COUNT = 2
        const val PENDING_DAYS = 30
        const val MAX_LEARNED = 5_000
        const val MAX_PENDING = 2_000
        const val MAX_LENGTH = 32
        private const val DAY_MS = 24L * 60 * 60 * 1000

        val EMPTY = PersonalWords(emptyMap())

        /** A stored list, with expired and surplus words dropped as of [now]. Later duplicates of a word win. */
        fun of(entries: Collection<LearnedWord>, now: Long): PersonalWords =
            PersonalWords(trim(entries.filter { isKeepable(it.text) }.associateBy { keyOf(it.text) }, now))

        private fun keyOf(word: String): String = word.lowercase()

        private fun isKeepable(word: String): Boolean =
            word.length in 2..MAX_LENGTH && Speller.looksLikeAWord(word) && word.count { it.isLetter() } >= 2

        private fun trim(words: Map<String, LearnedWord>, now: Long): Map<String, LearnedWord> {
            val cutoff = now - PENDING_DAYS * DAY_MS
            val (learned, pending) = words.entries.partition { it.value.count >= LEARN_COUNT }
            val keptPending = pending.filter { it.value.lastSeen >= cutoff }
            fun newest(list: List<Map.Entry<String, LearnedWord>>, max: Int) =
                if (list.size <= max) list else list.sortedByDescending { it.value.lastSeen }.take(max)
            return (newest(learned, MAX_LEARNED) + newest(keptPending, MAX_PENDING)).associate { it.key to it.value }
        }
    }
}
