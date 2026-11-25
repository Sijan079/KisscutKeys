package app.kisscut.keys.shared.core.words

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Swiping everyday words over the real shipped list: the traced word comes back first or second. */
class BundledSwipeTest {
    private val dir = File("../../ui/android/src/main/assets/words")
    private val matcher = SwipeMatcher(
        listOf(WordList.parse(File(dir, "en_US.txt").readText(), File(dir, "en_US_fixes.txt").readText())),
    )

    @Test
    fun everydayWordsComeBackOnTop() {
        val words = listOf(
            "hello", "world", "the", "you", "thanks", "tomorrow", "because", "what", "where", "love",
            "good", "night", "see", "later", "really", "people", "think", "about", "sorry", "happy",
        )
        val misses = mutableListOf<String>()
        var first = 0
        for (w in words) {
            val got = matcher.match(SwipeMatcherTest.pathFor(w), SwipeMatcherTest.layout)
            if (got.firstOrNull() == w) first++
            if (w !in got.take(2)) misses += "$w -> ${got.take(3)}"
        }
        assertTrue(misses.isEmpty(), "not in the top two: $misses")
        assertTrue(first >= words.size * 3 / 4, "only $first of ${words.size} first")
    }
}
