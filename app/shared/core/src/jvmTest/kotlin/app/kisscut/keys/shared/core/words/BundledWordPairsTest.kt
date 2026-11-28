package app.kisscut.keys.shared.core.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The real asset the app ships (docs/KEYBOARD.md, Word pairs). */
class BundledWordPairsTest {
    private val dir = File("../../ui/android/src/main/assets/words")
    private val list = WordList.parse(File(dir, "en_US.txt").readText(), File(dir, "en_US_fixes.txt").readText())
    private val text = File(dir, "en_US_pairs.txt").readText()
    private val pairs = WordPairs.parse(text)

    @Test
    fun everyWordIsInTheWordListAndNoNextWordIsOffensive() {
        val lines = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        assertTrue(pairs.size > 20_000, "only ${pairs.size} words")
        for (line in lines) {
            val previous = line.substringBefore('\t')
            assertTrue(list.isKnown(previous), previous)
            for (next in pairs.after(previous)) {
                val word = list.find(next.text)
                assertTrue(word != null && !word.offensive, "$previous ${next.text}")
            }
            assertTrue(pairs.after(previous).size in 1..12, previous)
        }
    }

    @Test
    fun everydayPairsComeFirst() {
        assertEquals("you", pairs.after("thank").first().text)
        assertEquals("to", pairs.after("going").first().text)
        assertEquals("know", pairs.after("you").first().text)
        val everyday = listOf("of" to "course", "I" to "think", "see" to "you", "hello" to "there", "good" to "morning")
        for ((previous, next) in everyday) {
            assertTrue(pairs.after(previous).any { it.text == next }, "$previous $next")
        }
    }

    @Test
    fun theCaseOfThePreviousWordDoesNotMatter() {
        assertEquals(pairs.after("of"), pairs.after("Of"))
        assertTrue(pairs.after("The").isNotEmpty())
    }
}
