package app.kisscut.keys.shared.core.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpellerTest {
    private val english = WordList(
        words = listOf(
            Word("the", 222), Word("I", 196), Word("for", 196), Word("not", 179), Word("can't", 160),
            Word("don't", 170), Word("I'm", 165), Word("tomorrow", 120), Word("tomato", 90), Word("hello", 120),
            Word("help", 140), Word("held", 110), Word("cant", 70), Word("bot", 78), Word("us", 160),
            Word("form", 150), Word("from", 190), Word("damn", 90, offensive = true), Word("dame", 76),
            Word("there", 200), Word("three", 170), Word("these", 180),
        ),
        fixes = mapOf("cant" to "can't", "bot" to "not", "alot" to "a lot"),
    )
    private val speller = Speller(listOf(english))

    @Test
    fun keysIgnoreCaseAndApostrophes() {
        assertEquals("dont", Speller.key("Don't"))
        assertEquals("im", Speller.key("I’m"))
    }

    @Test
    fun neighbouringKeysAndSwapsAreCheaperTypos() {
        assertTrue(Speller.areNeighbours('r', 't'))
        assertTrue(Speller.areNeighbours('a', 'q'))
        assertTrue(Speller.areNeighbours('s', 'z'))
        assertFalse(Speller.areNeighbours('a', 'p'))
        assertEquals(Speller.NEIGHBOUR_COST, Speller.typoCost("thr", "the"))
        assertEquals(1.0, Speller.typoCost("tha", "the"))
        assertEquals(Speller.SWAP_COST, Speller.typoCost("teh", "the"))
        assertEquals(Speller.DOUBLE_COST, Speller.typoCost("helo", "hello"))
        assertEquals(Speller.DOUBLE_COST, Speller.typoCost("helllo", "hello"))
        assertEquals(0.0, Speller.typoCost("hello", "hello"))
        assertTrue(Speller.typoCost("hello", "tomorrow", limit = 2.0) > 2.0)
    }

    @Test
    fun suggestsCorrectionsAndCompletionsInTheTypedCase() {
        assertEquals("the", speller.suggest("teh").first())
        assertEquals("The", speller.suggest("Teh").first())
        assertEquals("THE", speller.suggest("TEH").first())
        assertEquals(listOf("tomorrow", "tomato"), speller.suggest("tom").take(2))
        assertTrue("don't" in speller.suggest("dont"))
        assertTrue(speller.suggest("helo").containsAll(listOf("hello", "help")))
    }

    @Test
    fun aKnownWordComesFirstAsTyped() {
        assertEquals("hello", speller.suggest("hello").first())
        assertEquals("Held", speller.suggest("Held").first())
        // A fix pair from a real word is offered right after it, one tap away.
        assertEquals(listOf("bot", "not"), speller.suggest("bot").take(2))
    }

    @Test
    fun offensiveWordsAreNeverSuggestedOrUsedAsCorrections() {
        assertFalse("damn" in speller.suggest("damm"))
        assertFalse("damn" in speller.suggest("dam"))
        assertEquals("damn", speller.suggest("damn").first(), "typed as is, it stays")
        assertNull(speller.autocorrect("damn"))
        assertFalse(speller.autocorrect("damm") == "damn")
    }

    @Test
    fun nothingForNumbersSymbolsAndEmptyInput() {
        assertTrue(speller.suggest("").isEmpty())
        assertTrue(speller.suggest("h3llo").isEmpty())
        assertTrue(speller.suggest("@helo").isEmpty())
    }

    @Test
    fun autocorrectFixesClearOneTypoMistakes() {
        assertEquals("the", speller.autocorrect("teh"))
        assertEquals("The", speller.autocorrect("Teh"))
        assertEquals("tomorrow", speller.autocorrect("tomorow"))
        assertEquals("don't", speller.autocorrect("dont"))
        assertEquals("I'm", speller.autocorrect("im"))
        assertEquals("I", speller.autocorrect("i"))
        assertEquals("a lot", speller.autocorrect("alot"))
    }

    @Test
    fun autocorrectLeavesRealWordsAndRiskyInputAlone() {
        assertNull(speller.autocorrect("form"), "a real word, even if 'from' is more common")
        assertNull(speller.autocorrect("cant"), "fix pairs never override a real word")
        assertNull(speller.autocorrect("bot"))
        assertNull(speller.autocorrect("us"))
        assertNull(speller.autocorrect("Us"))
        assertNull(speller.autocorrect("TEH"), "all caps")
        assertNull(speller.autocorrect("tEh"), "mixed case")
        assertNull(speller.autocorrect("teh2"))
        assertNull(speller.autocorrect("#teh"))
        assertNull(speller.autocorrect("teh", reverted = setOf("teh")), "the user undid it in this field")
        assertNull(speller.autocorrect("thx"), "chat shorthand")
        assertNull(speller.autocorrect("Lol"))
    }

    @Test
    fun autocorrectNeedsAClearLeadAndOneTypoAtMost() {
        // "helo" is as close to "hello" (a missed double letter) as to "help" (a neighbouring key): it stays.
        assertNull(speller.autocorrect("helo"))
        // Two typos: applied from six letters on, only suggested in shorter words.
        assertEquals("tomorrow", speller.autocorrect("tmorow"))
        assertTrue("help" in speller.suggest("hxkp"))
        assertNull(speller.autocorrect("hxkp"))
        // Too short to guess.
        assertNull(speller.autocorrect("xz"))
    }

    @Test
    fun severalListsWorkAtOnce() {
        val tagalog = WordList(listOf(Word("kain", 150), Word("pero", 160)), emptyMap())
        val both = Speller(listOf(english, tagalog))
        assertNull(both.autocorrect("kain"), "a word from either list is known")
        assertEquals("pero", both.suggest("pro").first())
        assertEquals("the", both.autocorrect("teh"))
    }
}
