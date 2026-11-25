package app.kisscut.keys.shared.core.words

/** One change to the text field, in the order the keyboard applies them (docs/KEYBOARD.md, Typing pipeline). */
sealed interface Edit {
    /** Show [text] as the word being typed (underlined), replacing the previous one; "" removes it. */
    data class Compose(val text: String) : Edit

    /** Put [text] in the field for good, replacing the word being typed if there is one. */
    data class Commit(val text: String) : Edit

    /** Remove [count] characters before the cursor: only text this keyboard just put there. */
    data class DeleteBefore(val count: Int) : Edit

    /** Keep the word being typed as it is and stop underlining it. */
    data object FinishComposing : Edit

    /** A plain Backspace key event: the field deletes on its own. */
    data object DeleteKey : Edit
}

/**
 * The word being typed, built only from this keyboard's own key presses. Letters, digits and apostrophes extend the
 * word; anything else ends it. Ending it with a space or sentence punctuation can autocorrect it through
 * [corrector], and Backspace right after puts back what was typed and remembers that word for the rest of the
 * field, so it is not corrected again. Only a word that starts at a word boundary is ever corrected: "gmail" in
 * "john@gmail.com" or "teh" in "#teh" is not a word of its own. After this keyboard's own space or new line the
 * boundary is known; where it isn't (a new field with text in it, after the cursor moved, Enter, a sticker or a
 * Backspace) [charBefore] is asked once, when the next word starts: the keyboard checks the one character before
 * the cursor, or null at the start of the field (user's call, 2026-11-25). A swiped word ([swipe]) gets a space
 * before it when it needs one, and Backspace removes it whole.
 */
class Composer(
    private val corrector: (typed: String, reverted: Set<String>) -> String?,
    private val charBefore: () -> Char? = { 'x' },
) {
    private data class Undo(val typed: String, val fixed: String, val separator: String)

    private val word = StringBuilder()
    private val reverted = HashSet<String>()
    private var undo: Undo? = null

    /** Whether the next word starts at a word boundary; null when unknown, so [startsAtBoundary] is asked. */
    private var atBoundary: Boolean? = true

    /** Whether the word being typed started at such a boundary. */
    private var wordAtBoundary = false

    /** The last character this keyboard committed, while it is still right before the cursor; null if unknown. */
    private var lastCommitted: Char? = null

    /** The word being typed came from a swipe (Backspace removes it whole). */
    private var swiped = false

    /** The word being typed, or "" between words. */
    val current: String get() = word.toString()

    /** Backspace now would undo the last autocorrection. */
    val canUndo: Boolean get() = undo != null

    /** The word being typed may be autocorrected when it ends (it stands on its own). */
    val mayCorrect: Boolean get() = word.isNotEmpty() && wordAtBoundary

    /** Words the user undid or kept in this field, for previewing a correction elsewhere (a copy). */
    val revertedWords: Set<String> get() = reverted.toSet()

    /** Text from a key. [autocorrect] says whether a word ended by this key may be corrected. */
    fun type(text: String, autocorrect: Boolean): List<Edit> {
        if (text.isNotEmpty() && text.all(::isWordChar)) {
            undo = null
            swiped = false
            if (word.isEmpty()) wordAtBoundary = atBoundary ?: charBefore().let { it == null || it.isWhitespace() }
            word.append(text)
            return listOf(Edit.Compose(current))
        }
        return endWord(text, autocorrect && text in CORRECTING)
    }

    /** Backspace: shorten the word, undo the last autocorrection, or let the field delete. */
    fun backspace(): List<Edit> {
        if (word.isNotEmpty()) {
            undo = null
            if (swiped) word.clear() else word.deleteAt(word.length - 1)
            swiped = false
            return listOf(Edit.Compose(current))
        }
        val last = undo ?: run {
            // The field deletes something this keyboard can't see: what comes before the cursor is unknown.
            atBoundary = null
            lastCommitted = null
            return listOf(Edit.DeleteKey)
        }
        undo = null
        reverted += Speller.key(last.typed)
        val written = last.fixed.length + last.separator.length
        return listOf(Edit.DeleteBefore(written), Edit.Commit(last.typed + last.separator))
    }

    /**
     * A tapped suggestion: [text] replaces the word being typed, followed by a space. [keep] means the user chose
     * what they typed over a correction, so that word is not corrected again in this field.
     */
    fun pick(text: String, keep: Boolean): List<Edit> {
        if (word.isEmpty()) return emptyList()
        if (keep) reverted += Speller.key(current)
        word.clear()
        swiped = false
        undo = null
        atBoundary = true
        lastCommitted = ' '
        return listOf(Edit.Commit("$text "))
    }

    /**
     * A swiped word: a word still being typed is kept, with a space after it; otherwise a space goes first unless
     * the cursor already follows a space, a new line, the field start or an opening character ("@", "#", "(").
     * The swiped word is then the word being typed (underlined), so the strip can offer the other matches.
     */
    fun swipe(text: String): List<Edit> {
        undo = null
        val edits = ArrayList<Edit>()
        if (word.isNotEmpty()) {
            // The word being typed must be in this commit: a commit replaces the underlined word.
            edits += Edit.Commit("$current ")
            word.clear()
        } else {
            val before = when {
                atBoundary == true -> null
                lastCommitted != null -> lastCommitted
                else -> charBefore()
            }
            if (before != null && !before.isWhitespace() && before !in OPENERS) edits += Edit.Commit(" ")
        }
        lastCommitted = null
        atBoundary = true
        word.append(text)
        wordAtBoundary = true
        swiped = true
        edits += Edit.Compose(current)
        return edits
    }

    /**
     * Keep the word as typed before anything that isn't a key: Enter, a sticker or emoji, a cursor slide, switching
     * keyboards. Undo is gone afterwards.
     */
    fun finish(): List<Edit> {
        undo = null
        atBoundary = null
        lastCommitted = null
        swiped = false
        if (word.isEmpty()) return emptyList()
        word.clear()
        return listOf(Edit.FinishComposing)
    }

    /** The cursor moved somewhere this keyboard didn't put it: forget the word and the undo (no edits). */
    fun reset() {
        word.clear()
        undo = null
        atBoundary = null
        lastCommitted = null
        swiped = false
    }

    /** A new field: also forget which words the user undid. [atStart]: the cursor is at the very start of it. */
    fun newField(atStart: Boolean) {
        reset()
        reverted.clear()
        atBoundary = if (atStart) true else null
    }

    private fun endWord(separator: String, correct: Boolean): List<Edit> {
        undo = null
        val startedAtBoundary = wordAtBoundary
        swiped = false
        atBoundary = separator.isNotEmpty() && separator.last().isWhitespace()
        lastCommitted = separator.lastOrNull()
        if (word.isEmpty()) return listOf(Edit.Commit(separator))
        val typed = current
        word.clear()
        val fixed = if (correct && startedAtBoundary) corrector(typed, reverted)?.takeIf { it != typed } else null
        if (fixed != null) undo = Undo(typed, fixed, separator)
        return listOf(Edit.Commit((fixed ?: typed) + separator))
    }

    companion object {
        /** Keys that may autocorrect the word they end. Other symbols ("@", "/", "-") keep it as typed. */
        val CORRECTING = setOf(" ", ".", ",", "!", "?", ";", ":", "\n")

        fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '’'

        /** Characters a swiped word follows with no space ("@name", "#tag", "(word"). */
        const val OPENERS = "@#(/[{<\"'-“‘"

    }
}
