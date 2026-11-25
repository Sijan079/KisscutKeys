package app.kisscut.keys.shared.core.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordListTest {
    private val words = """
        # header
        the	222
        US	160
        us	150

        damn	90	o
    """.trimIndent()
    private val fixes = "# header\ndont\tdon't\r\nalot\ta lot\n"

    @Test
    fun parsesWordsFrequenciesAndMarks() {
        val list = WordList.parse(words, fixes)
        assertEquals(listOf("the", "US", "us", "damn"), list.words.map { it.text })
        assertEquals(Word("damn", 90, offensive = true), list.find("damn"))
        assertEquals(mapOf("dont" to "don't", "alot" to "a lot"), list.fixes)
    }

    @Test
    fun lookupsIgnoreCaseAndTheMoreCommonSpellingWins() {
        val list = WordList.parse(words, fixes)
        assertEquals("the", list.find("The")?.text)
        assertEquals("US", list.find("us")?.text)
        assertTrue(list.isKnown("DAMN"))
        assertFalse(list.isKnown("teh"))
        assertNull(list.find(""))
    }

    @Test
    fun malformedLinesFailWithTheirLineNumber() {
        val badFrequency = assertFailsWith<IllegalArgumentException> { WordList.parse("ok\t5\nbad\t999", "") }
        assertTrue("line 2" in badFrequency.message.orEmpty(), badFrequency.message)
        assertFailsWith<IllegalArgumentException> { WordList.parse("word", "") }
        assertFailsWith<IllegalArgumentException> { WordList.parse("word\t5\tx", "") }
        assertFailsWith<IllegalArgumentException> { WordList.parse("", "typed-only") }
    }
}
