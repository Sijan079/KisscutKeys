package app.kisscut.keys.shared.core.words

/** A word that often follows another (docs/KEYBOARD.md, Word pairs). [score] is 1-255, higher is more likely. */
data class NextWord(val text: String, val score: Int)

/**
 * The bundled word-pair table: for a previous word, the words that most often come next, most likely first. Kept
 * compact (half a million pairs live in the keyboard): each word's text is one shared string, scores are bytes.
 */
class WordPairs private constructor(private val byPrevious: Map<String, Followers>) {
    private class Followers(val words: Array<String>, val scores: ByteArray)

    /** Words that often follow [previous], typed in any case; empty when the table doesn't have it. */
    fun after(previous: String): List<NextWord> = byPrevious[WordList.key(previous)]?.let { f ->
        f.words.indices.map { NextWord(f.words[it], f.scores[it].toInt() and 0xFF) }
    }.orEmpty()

    /** How likely [next] is to follow [previous], 1-255, or 0 if the table doesn't pair them. Case aside. */
    fun score(previous: String, next: String): Int {
        val f = byPrevious[WordList.key(previous)] ?: return 0
        for (i in f.words.indices) if (f.words[i].equals(next, ignoreCase = true)) return f.scores[i].toInt() and 0xFF
        return 0
    }

    val size: Int get() = byPrevious.size

    companion object {
        /**
         * Parses the generated file (docs/KEYBOARD.md, Word pairs): `previous<TAB>next score[<TAB>next score...]`
         * lines, most likely first; `#` lines and blank lines are skipped. A malformed line throws, naming its line
         * number, so a broken asset fails in tests rather than on a phone.
         */
        fun parse(text: String): WordPairs {
            val byPrevious = HashMap<String, Followers>()
            val shared = HashMap<String, String>()
            text.lineSequence().forEachIndexed { i, raw ->
                val line = raw.trimEnd('\r')
                if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
                val parts = line.split('\t')
                fun bad(): Nothing = throw IllegalArgumentException("word pairs line ${i + 1}: \"$line\"")
                if (parts.size < 2 || parts[0].isEmpty()) bad()
                val words = Array(parts.size - 1) { "" }
                val scores = ByteArray(parts.size - 1)
                for (j in 1 until parts.size) {
                    val part = parts[j]
                    val space = part.lastIndexOf(' ')
                    val score = part.substring(space + 1).toIntOrNull()
                    if (space <= 0 || score == null || score !in 1..255) bad()
                    if (j > 1 && score > (scores[j - 2].toInt() and 0xFF)) bad()
                    val word = part.substring(0, space)
                    words[j - 1] = shared.getOrPut(word) { word }
                    scores[j - 1] = score.toByte()
                }
                if (byPrevious.put(WordList.key(parts[0]), Followers(words, scores)) != null) bad()
            }
            return WordPairs(byPrevious)
        }
    }
}
