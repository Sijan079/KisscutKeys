package app.kisscut.keys.keyboard

/** What a suggestion slot shows (docs/KEYBOARD.md, Suggestion strip). */
enum class SlotKind {
    /** The best word: bold, in the middle. */
    BEST,

    /** What Space will autocorrect to: bold, in the middle, with a lime bar. */
    FIX,

    /** The word as typed, in quotes, so one tap keeps it instead of the fix. */
    KEEP,

    /** Another choice. */
    ALT,
}

/** One slot: [label] is shown, [word] goes into the field. */
data class StripSlot(val label: String, val word: String, val kind: SlotKind)

/**
 * The three slots, left to right, for [typed] given the speller's [suggestions] (best first) and the [fix] Space
 * would apply, or an empty list when there is nothing to show. With a fix: the typed word in quotes, the fix in the
 * middle, then the next choice. Without: second choice, best, third choice. A null slot stays blank.
 */
fun stripSlots(typed: String, suggestions: List<String>, fix: String?): List<StripSlot?> {
    if (typed.isEmpty()) return emptyList()
    if (fix != null) {
        val next = suggestions.firstOrNull { it != fix && it != typed }
        return listOf(
            StripSlot("\u201C$typed\u201D", typed, SlotKind.KEEP),
            StripSlot(fix, fix, SlotKind.FIX),
            next?.let { StripSlot(it, it, SlotKind.ALT) },
        )
    }
    if (suggestions.isEmpty()) return emptyList()
    fun alt(i: Int) = suggestions.getOrNull(i)?.let { StripSlot(it, it, SlotKind.ALT) }
    return listOf(alt(1), StripSlot(suggestions[0], suggestions[0], SlotKind.BEST), alt(2))
}
