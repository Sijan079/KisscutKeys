package app.kisscut.keys.keyboard

import org.junit.Test
import java.text.BreakIterator
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmojiCatalogTest {
    @Test
    fun everyCategoryHasEmoji() {
        for (category in EmojiCategory.entries) assertTrue(category.emoji.size >= 20, "$category is nearly empty")
    }

    @Test
    fun entriesAreSingleEmojiWithNoBlanks() {
        for (category in EmojiCategory.entries) {
            for (e in category.emoji) {
                assertTrue(e.isNotBlank(), "blank entry in $category")
                val graphemes = BreakIterator.getCharacterInstance().apply { setText(e) }
                var count = 0
                while (graphemes.next() != BreakIterator.DONE) count++
                assertEquals(1, count, "\"$e\" in $category is not one emoji")
                assertTrue(e.none { it.isLetterOrDigit() && it.code < 128 }, "\"$e\" in $category is plain text")
            }
        }
    }

    @Test
    fun symbolsThatDefaultToTextAskForColorEmoji() {
        // Without U+FE0F, symbols like the heart or the plane draw (and send) as black-and-white text.
        for (category in EmojiCategory.entries) {
            for (e in category.emoji) {
                val first = e.codePointAt(0)
                if (!Character.isEmojiPresentation(first)) {
                    assertTrue(e.contains('\uFE0F'), "\"$e\" in $category needs U+FE0F to show in color")
                }
            }
        }
    }

    @Test
    fun noEmojiIsListedTwiceInACategory() {
        for (category in EmojiCategory.entries) {
            val dupes = category.emoji.groupBy { it }.filterValues { it.size > 1 }.keys
            assertTrue(dupes.isEmpty(), "$category repeats $dupes")
        }
    }
}
