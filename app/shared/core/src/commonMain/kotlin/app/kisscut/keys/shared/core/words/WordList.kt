package app.kisscut.keys.shared.core.words

/**
 * One word of the bundled list (docs/KEYBOARD.md, Word list). [frequency] is the source list's 0-255 score, not a
 * count. [offensive] words are known (never "corrected") but never suggested or used as a correction.
 */
data class Word(val text: String, val frequency: Int, val offensive: Boolean = false)

/**
 * A parsed word list and its fix pairs ("dont" to "don't"). Lookups ignore case; [words] keeps the list's order,
 * most common first.
 */
class WordList(val words: List<Word>, val fixes: Map<String, String>) {
    private val byKey: Map<String, Word> = buildMap {
        // The list is sorted by frequency, so the first spelling of a key ("us" before "US") wins.
        for (w in words) putIfAbsent(key(w.text), w)
    }

    /** The word typed as [text] in any case, or null if the list doesn't have it. */
    fun find(text: String): Word? = byKey[key(text)]

    fun isKnown(text: String): Boolean = find(text) != null

    companion object {
        fun key(text: String): String = text.lowercase()

        /**
         * Parses the generated files (`tools/wordlist/build_en.py`): [words] has `word<TAB>frequency[<TAB>o]`
         * lines, [fixes] has `typed<TAB>fix` lines; `#` lines and blank lines are skipped. A malformed line throws,
         * naming its line number, so a broken asset fails in tests rather than on a phone.
         */
        fun parse(words: String, fixes: String): WordList = WordList(parseWords(words), parseFixes(fixes))

        private fun parseWords(text: String): List<Word> = lines(text).map { (n, line) ->
            val parts = line.split('\t')
            val frequency = parts.getOrNull(1)?.toIntOrNull()
            require(parts.size in 2..3 && parts[0].isNotEmpty() && frequency != null && frequency in 0..255) {
                "word list line $n: \"$line\""
            }
            require(parts.size == 2 || parts[2] == "o") { "word list line $n: unknown mark \"${parts[2]}\"" }
            Word(parts[0], frequency, offensive = parts.size == 3)
        }

        private fun parseFixes(text: String): Map<String, String> = lines(text).associate { (n, line) ->
            val parts = line.split('\t')
            require(parts.size == 2 && parts.all { it.isNotEmpty() }) { "fix list line $n: \"$line\"" }
            parts[0] to parts[1]
        }

        private fun lines(text: String): List<Pair<Int, String>> = text.lineSequence()
            .mapIndexed { i, line -> (i + 1) to line.trimEnd('\r') }
            .filter { (_, line) -> line.isNotBlank() && !line.startsWith("#") }
            .toList()
    }
}
