package app.kisscut.keys.shared.core.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WordPairsTest {
    private val text = "# header\nof\tcourse 255\tthe 240\r\n\nI\tdon't 200\ta lot 90\n"

    @Test
    fun parsesNextWordsMostLikelyFirst() {
        val pairs = WordPairs.parse(text)
        assertEquals(2, pairs.size)
        assertEquals(listOf(NextWord("course", 255), NextWord("the", 240)), pairs.after("of"))
        assertEquals(NextWord("a lot", 90), pairs.after("I")[1])
    }

    @Test
    fun lookupsIgnoreCase() {
        val pairs = WordPairs.parse(text)
        assertEquals("course", pairs.after("Of").first().text)
        assertEquals("don't", pairs.after("i").first().text)
        assertTrue(pairs.after("teh").isEmpty())
    }

    @Test
    fun malformedLinesFailWithTheirLineNumber() {
        for (bad in listOf("of", "of\tcourse", "of\tcourse x", "of\tcourse 0", "of\tcourse 256", "\tthe 9", "of\t 9",
            "of\tthe 9\tcourse 10")) {
            val e = assertFailsWith<IllegalArgumentException>(bad) { WordPairs.parse("# h\n$bad\n") }
            assertTrue("line 2" in e.message!!, bad)
        }
        assertFailsWith<IllegalArgumentException> { WordPairs.parse("of\tthe 9\nOf\tcourse 9\n") }
    }
}
