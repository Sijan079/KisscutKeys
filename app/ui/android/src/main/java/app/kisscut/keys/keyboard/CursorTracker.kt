package app.kisscut.keys.keyboard

import app.kisscut.keys.shared.core.words.Edit

/**
 * Where the cursor should be after this keyboard's own edits, so a cursor move the user made (a tap elsewhere,
 * the app changing the text) is told apart from the selection updates our edits cause (docs/KEYBOARD.md, Typing
 * pipeline). Works from lengths alone: the field is never read. Updates can arrive late, one per batch of edits,
 * so every position our batches lead to is remembered until Android reports it.
 */
class CursorTracker {
    private var selStart = UNKNOWN
    private var selEnd = UNKNOWN
    private var composeStart = UNKNOWN
    private val expected = ArrayDeque<Int>()

    /**
     * Where our underlined word should be, start to end, or null when we don't know. Re-marking it before each edit
     * keeps a word an app quietly stopped underlining from being typed twice.
     */
    val composingRegion: Pair<Int, Int>?
        get() = if (composeStart != UNKNOWN && selStart != UNKNOWN && selStart == selEnd && composeStart <= selStart) {
            composeStart to selStart
        } else {
            null
        }

    /** Android has reported the result of our latest edits (no update of ours is still on its way). */
    val caughtUp: Boolean get() = expected.isEmpty()

    /** A new field, or the cursor moved in a way we can't predict (arrow keys, a sticker): trust the next update. */
    fun forget(start: Int = UNKNOWN, end: Int = start, composing: Int = UNKNOWN) {
        selStart = start
        selEnd = end
        composeStart = composing
        expected.clear()
    }

    /** Record one batch of [edits] just sent to the field. */
    fun applied(edits: List<Edit>) {
        if (selStart == UNKNOWN || edits.isEmpty()) return
        for (edit in edits) {
            when (edit) {
                is Edit.Compose -> {
                    val base = if (composeStart != UNKNOWN) composeStart else selStart
                    composeStart = if (edit.text.isEmpty()) UNKNOWN else base
                    moveTo(base + edit.text.length)
                }
                is Edit.Commit -> {
                    val base = if (composeStart != UNKNOWN) composeStart else selStart
                    composeStart = UNKNOWN
                    moveTo(base + edit.text.length)
                }
                is Edit.DeleteBefore -> moveTo(maxOf(0, selStart - edit.count))
                Edit.FinishComposing -> composeStart = UNKNOWN
                Edit.DeleteKey -> moveTo(if (selStart != selEnd) selStart else maxOf(0, selStart - 1))
            }
        }
        expected.addLast(selStart)
    }

    /**
     * Android reported the selection (and where the underlined word starts, or -1). Returns true when it is one our
     * edits lead to (or we had no idea yet), false when something else moved the cursor; the tracker then follows.
     */
    fun onUpdate(newStart: Int, newEnd: Int, composingStart: Int = UNKNOWN): Boolean {
        if (selStart == UNKNOWN) {
            forget(newStart, newEnd, composingStart)
            return true
        }
        val i = if (newStart == newEnd) expected.indexOf(newStart) else -1
        if (i >= 0) {
            repeat(i + 1) { expected.removeFirst() }
            return true
        }
        if (expected.isEmpty() && newStart == selStart && newEnd == selEnd) return true
        forget(newStart, newEnd)
        return false
    }

    private fun moveTo(position: Int) {
        selStart = position
        selEnd = position
    }

    private companion object {
        const val UNKNOWN = -1
    }
}
