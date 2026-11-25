package app.kisscut.keys.keyboard

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestionStripTest {
    @Test
    fun bestInTheMiddleWithTheOthersAround() {
        assertEquals(
            listOf(
                StripSlot("tomato", "tomato", SlotKind.ALT),
                StripSlot("tomorrow", "tomorrow", SlotKind.BEST),
                StripSlot("tom", "tom", SlotKind.ALT),
            ),
            stripSlots("tomo", listOf("tomorrow", "tomato", "tom"), fix = null),
        )
        assertEquals(listOf(null, StripSlot("hi", "hi", SlotKind.BEST), null), stripSlots("hi", listOf("hi"), null))
    }

    @Test
    fun aComingFixSitsInTheMiddleWithTheTypedWordToKeep() {
        assertEquals(
            listOf(
                StripSlot("“teh”", "teh", SlotKind.KEEP),
                StripSlot("the", "the", SlotKind.FIX),
                StripSlot("ten", "ten", SlotKind.ALT),
            ),
            stripSlots("teh", listOf("the", "ten", "Tehran"), fix = "the"),
        )
        assertEquals(null, stripSlots("dont", listOf("don't"), fix = "don't")[2], "nothing else to offer")
    }

    @Test
    fun nothingToShowBetweenWordsOrWithoutSuggestions() {
        assertTrue(stripSlots("", listOf("the"), null).isEmpty())
        assertTrue(stripSlots("xqz", emptyList(), null).isEmpty())
    }
}
