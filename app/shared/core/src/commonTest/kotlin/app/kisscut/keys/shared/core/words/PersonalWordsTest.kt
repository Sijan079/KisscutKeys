package app.kisscut.keys.shared.core.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalWordsTest {
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_000 * day

    @Test
    fun aWordTypedTwiceIsLearned() {
        val once = PersonalWords.EMPTY.typed("kain", t0)
        assertFalse(once.isLearned("kain"))
        assertEquals(emptyList(), once.known)
        val twice = once.typed("kain", t0 + 1)
        assertTrue(twice.isLearned("kain"))
        assertEquals(listOf("kain"), twice.known)
    }

    @Test
    fun undoingAnAutocorrectLearnsTheWordAtOnce() {
        val words = PersonalWords.EMPTY.undidAutocorrect("charot", t0)
        assertTrue(words.isLearned("charot"))
    }

    @Test
    fun countingIgnoresCase() {
        val words = PersonalWords.EMPTY.typed("Juan", t0).typed("juan", t0 + 1)
        assertTrue(words.isLearned("JUAN"))
    }

    @Test
    fun lowerCaseSpellingWinsOnceTyped() {
        val sentenceStartFirst = PersonalWords.EMPTY.typed("Kain", t0).typed("kain", t0 + 1)
        assertEquals(listOf("kain"), sentenceStartFirst.known)
        val nameAlwaysCapitalised = PersonalWords.EMPTY.typed("Juan", t0).typed("Juan", t0 + 1)
        assertEquals(listOf("Juan"), nameAlwaysCapitalised.known)
        val staysLower = PersonalWords.EMPTY.typed("kain", t0).typed("Kain", t0 + 1)
        assertEquals(listOf("kain"), staysLower.known)
    }

    @Test
    fun wordsWithDigitsOrSymbolsSingleLettersAndLongRunsAreNotKept() {
        var words = PersonalWords.EMPTY
        for (w in listOf("abc123", "a@b", "x", "-", "a".repeat(PersonalWords.MAX_LENGTH + 1), "'s")) {
            words = words.undidAutocorrect(w, t0)
        }
        assertEquals(0, words.entries.size)
        assertTrue(PersonalWords.EMPTY.undidAutocorrect("mag-aral", t0).isLearned("mag-aral"))
    }

    @Test
    fun aWordSeenOnceExpiresAfterThirtyDays() {
        val words = PersonalWords.EMPTY.typed("sana", t0).typed("kain", t0).typed("kain", t0)
        val later = words.pruned(t0 + PersonalWords.PENDING_DAYS * day + 1)
        assertEquals(listOf("kain"), later.entries.map { it.text })
        val justInTime = words.pruned(t0 + PersonalWords.PENDING_DAYS * day)
        assertEquals(setOf("sana", "kain"), justInTime.entries.map { it.text }.toSet())
    }

    @Test
    fun typingAgainKeepsAPendingWordAlive() {
        val words = PersonalWords.EMPTY.typed("sana", t0)
        val later = t0 + 29 * day
        val again = words.typed("sana", later).pruned(later + 29 * day)
        assertTrue(again.isLearned("sana"))
    }

    @Test
    fun theOldestWordsGoFirstPastTheCaps() {
        var words = PersonalWords.EMPTY
        for (i in 0..PersonalWords.MAX_LEARNED) words = words.undidAutocorrect(name(i), t0 + i)
        assertEquals(PersonalWords.MAX_LEARNED, words.known.size)
        assertFalse(words.isLearned(name(0)))
        assertTrue(words.isLearned(name(1)))
        for (i in 0..PersonalWords.MAX_PENDING) words = words.typed("p" + name(i), t0 + i)
        assertEquals(PersonalWords.MAX_LEARNED + PersonalWords.MAX_PENDING, words.entries.size)
        assertFalse(words.entries.any { it.text == "p" + name(0) })
    }

    @Test
    fun aStoredListLoadsWithoutExpiredOrBrokenWords() {
        val stored = listOf(
            LearnedWord("kain", 3, t0),
            LearnedWord("sana", 1, t0 - 31 * day),
            LearnedWord("ok1", 5, t0),
        )
        val words = PersonalWords.of(stored, now = t0)
        assertEquals(listOf("kain"), words.entries.map { it.text })
        assertEquals(3, words.entries.single().count)
    }

    /** Distinct letters-only words: "aa", "ab", ... */
    private fun name(i: Int): String = buildString {
        var n = i
        repeat(3) { append('a' + n % 26); n /= 26 }
    }
}
