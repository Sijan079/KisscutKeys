package app.kisscut.keys.keyboard

import app.kisscut.keys.shared.core.words.Edit
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorTrackerTest {
    private val tracker = CursorTracker().apply { forget(10) }

    @Test
    fun ourOwnEditsAreRecognisedEvenWhenUpdatesArriveLate() {
        tracker.applied(listOf(Edit.Compose("t")))
        tracker.applied(listOf(Edit.Compose("te")))
        tracker.applied(listOf(Edit.Compose("teh")))
        tracker.applied(listOf(Edit.Commit("the ")))
        // Android reports some of the steps, in order, after the fact.
        assertTrue(tracker.onUpdate(11, 11))
        assertTrue(tracker.onUpdate(13, 13))
        assertTrue(tracker.onUpdate(14, 14))
        // Undo: delete "the " and put back "teh ".
        tracker.applied(listOf(Edit.DeleteBefore(4), Edit.Commit("teh ")))
        assertTrue(tracker.onUpdate(14, 14))
    }

    @Test
    fun aTapElsewhereIsNotOurs() {
        tracker.applied(listOf(Edit.Compose("ab")))
        assertTrue(tracker.onUpdate(12, 12))
        assertFalse(tracker.onUpdate(3, 3), "the user tapped earlier in the text")
        // From there on the tracker follows the new position.
        tracker.applied(listOf(Edit.Commit("x")))
        assertTrue(tracker.onUpdate(4, 4))
    }

    @Test
    fun aSelectionIsNeverOurs() {
        tracker.applied(listOf(Edit.Compose("ab")))
        assertFalse(tracker.onUpdate(10, 12))
    }

    @Test
    fun backspaceKeyAndFinishMoveTheCursorAsTheFieldWould() {
        tracker.applied(listOf(Edit.DeleteKey))
        assertTrue(tracker.onUpdate(9, 9))
        tracker.applied(listOf(Edit.Compose("ok"), Edit.FinishComposing))
        assertTrue(tracker.onUpdate(11, 11))
        tracker.applied(listOf(Edit.Commit(" ")))
        assertTrue(tracker.onUpdate(12, 12), "after finishing, a commit goes at the cursor")
    }

    @Test
    fun anUnknownStartTrustsTheFirstUpdate() {
        val fresh = CursorTracker()
        fresh.applied(listOf(Edit.Compose("a")))
        assertTrue(fresh.onUpdate(42, 42, composingStart = 41))
        fresh.applied(listOf(Edit.Compose("ab")))
        assertTrue(fresh.onUpdate(43, 43))
    }

    @Test
    fun caughtUpOnlyOnceTheLatestEditIsReported() {
        tracker.applied(listOf(Edit.Compose("t")))
        tracker.applied(listOf(Edit.Compose("te")))
        assertFalse(tracker.caughtUp)
        assertTrue(tracker.onUpdate(11, 11))
        assertFalse(tracker.caughtUp, "a late update for an earlier keystroke")
        assertTrue(tracker.onUpdate(12, 12))
        assertTrue(tracker.caughtUp)
    }

    @Test
    fun knowsWhereItsUnderlinedWordIs() {
        kotlin.test.assertNull(tracker.composingRegion)
        tracker.applied(listOf(Edit.Compose("hey")))
        kotlin.test.assertEquals(10 to 13, tracker.composingRegion)
        tracker.applied(listOf(Edit.Commit("hey ")))
        kotlin.test.assertNull(tracker.composingRegion)
        tracker.applied(listOf(Edit.Compose("a")))
        kotlin.test.assertEquals(14 to 15, tracker.composingRegion)
        tracker.forget()
        kotlin.test.assertNull(tracker.composingRegion)
    }
}
