package app.kisscut.keys.shared.core.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The engine over the real shipped list: everyday typos are fixed, real words are left alone. */
class BundledSpellerTest {
    private val dir = File("../../ui/android/src/main/assets/words")
    private val speller = Speller(
        listOf(WordList.parse(File(dir, "en_US.txt").readText(), File(dir, "en_US_fixes.txt").readText())),
    )

    @Test
    fun fixesEverydayTypos() {
        val expected = mapOf(
            "teh" to "the", "dont" to "don't", "im" to "I'm", "i" to "I", "recieve" to "receive",
            "becuase" to "because", "tomorow" to "tomorrow", "alot" to "a lot", "wierd" to "weird",
            "wlrld" to "world", "wlrdld" to "world",
            "thx" to null, "lol" to null, "idk" to null, "brb" to null, "haha" to null,
        )
        for ((typed, fix) in expected) assertEquals(fix, speller.autocorrect(typed), typed)
    }

    @Test
    fun leavesRealWordsAlone() {
        for (w in listOf("form", "cant", "lets", "ill", "hid", "us", "bot", "nut", "were", "its", "your", "then")) {
            assertNull(speller.autocorrect(w), w)
        }
    }

    @Test
    fun suggestsCompletionsForAPrefix() {
        assertTrue("tomorrow" in speller.suggest("tomo"), speller.suggest("tomo").toString())
        assertTrue("because" in speller.suggest("beca"), speller.suggest("beca").toString())
    }
}
