package app.kisscut.keys.shared.core.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The real asset the app ships (built by tools/wordlist/build_en.py). */
class BundledWordListTest {
    private val dir = File("../../ui/android/src/main/assets/words")
    private val list = WordList.parse(File(dir, "en_US.txt").readText(), File(dir, "en_US_fixes.txt").readText())

    @Test
    fun hasTheAgreedSizeMostCommonFirst() {
        assertEquals(50_000, list.words.size)
        assertEquals("the", list.words.first().text)
        assertEquals(list.words.map { it.frequency }.sortedDescending(), list.words.map { it.frequency })
        assertTrue(list.words.all { it.frequency > 0 })
    }

    @Test
    fun knowsEverydayWordsAndMarksOffensiveOnes() {
        for (w in listOf("hello", "don't", "I'll", "gonna", "tomorrow")) assertTrue(list.isKnown(w), w)
        assertTrue(list.words.any { it.offensive })
        assertFalse(list.find("hello")!!.offensive)
    }

    @Test
    fun fixPairsComeFromTheSource() {
        assertEquals("can't", list.fixes["cant"])
        assertEquals("I'm", list.fixes["im"])
        assertEquals("a lot", list.fixes["alot"])
        assertEquals(99, list.fixes.size)
    }
}
