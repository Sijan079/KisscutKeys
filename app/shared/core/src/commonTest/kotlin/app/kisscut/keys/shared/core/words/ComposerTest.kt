package app.kisscut.keys.shared.core.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerTest {
    private val seenReverted = mutableListOf<Set<String>>()

    /** What the character before the cursor says when the keyboard has to check; counts the checks. */
    private var boundaryBefore = false
    private var charBeforeCursor: Char? = 'x'
    private var checks = 0
    private val composer = Composer(
        corrector = { typed, reverted ->
            seenReverted += reverted.toSet()
            if (typed.lowercase() == "teh" && "teh" !in reverted) "the" else null
        },
        charBefore = {
            checks++
            if (boundaryBefore) ' ' else charBeforeCursor
        },
        learner = { word, meant -> learned += word to meant },
    )

    /** What the learner heard: word and whether the user meant it over a correction. */
    private val learned = mutableListOf<Pair<String, Boolean>>()

    private fun typeAll(text: String, autocorrect: Boolean = true) =
        text.map { composer.type(it.toString(), autocorrect) }.last()

    @Test
    fun lettersBuildTheWordAndSpaceCommitsIt() {
        assertEquals(listOf(Edit.Compose("h")), composer.type("h", true))
        assertEquals(listOf(Edit.Compose("hi")), composer.type("i", true))
        assertEquals("hi", composer.current)
        assertEquals(listOf(Edit.Commit("hi ")), composer.type(" ", true))
        assertEquals("", composer.current)
        assertEquals(listOf(Edit.Commit(" ")), composer.type(" ", true), "a space between words is just a space")
    }

    @Test
    fun digitsAndApostrophesStayInTheWord() {
        typeAll("don't")
        assertEquals("don't", composer.current)
        typeAll("2")
        assertEquals("don't2", composer.current)
    }

    @Test
    fun spaceOrPunctuationAutocorrectsAndBackspaceUndoesIt() {
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("the ")), composer.type(" ", true))
        assertTrue(composer.canUndo)
        assertEquals(listOf(Edit.DeleteBefore(4), Edit.Commit("teh ")), composer.backspace())
        assertFalse(composer.canUndo)
        // The undone word is never corrected again in this field.
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("teh.")), composer.type(".", true))
        assertEquals(setOf("teh"), seenReverted.last())
        // A new field forgets it.
        composer.newField(atStart = true)
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("the,")), composer.type(",", true))
    }

    @Test
    fun noCorrectionWhenOffOrForOtherSymbols() {
        typeAll("teh", autocorrect = false)
        assertEquals(listOf(Edit.Commit("teh ")), composer.type(" ", false))
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("teh@")), composer.type("@", true))
        assertFalse(composer.canUndo)
    }

    @Test
    fun backspaceShortensTheWordThenDeletesNormally() {
        typeAll("ab")
        assertEquals(listOf(Edit.Compose("a")), composer.backspace())
        assertEquals(listOf(Edit.Compose("")), composer.backspace())
        assertEquals(listOf(Edit.DeleteKey), composer.backspace())
    }

    @Test
    fun undoIsOnlyOfferedRightAfterTheCorrection() {
        typeAll("teh")
        composer.type(" ", true)
        composer.type("x", true)
        composer.backspace()
        assertEquals(listOf(Edit.DeleteKey), composer.backspace(), "typing after the correction ends the undo")

        typeAll("teh")
        composer.type(" ", true)
        composer.reset()
        assertEquals(listOf(Edit.DeleteKey), composer.backspace(), "the cursor moved away from the correction")
    }

    @Test
    fun onlyWordsAfterASpaceOrNewLineAreCorrected() {
        assertEquals(listOf(Edit.Commit("@")), composer.type("@", true))
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("teh ")), composer.type(" ", true), "a handle")

        typeAll("#teh")
        assertEquals(listOf(Edit.Commit("teh ")), composer.type(" ", true), "a hashtag")

        typeAll("a.teh")
        assertEquals(listOf(Edit.Commit("teh ")), composer.type(" ", true))

        typeAll("ok\nteh")
        assertEquals(listOf(Edit.Commit("the ")), composer.type(" ", true), "after a new line")
    }

    @Test
    fun whereTheBoundaryIsUnknownTheCharacterBeforeIsCheckedOnce() {
        composer.newField(atStart = false)
        boundaryBefore = false
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("teh ")), composer.type(" ", true), "glued to text already there")
        assertEquals(1, checks)
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("the ")), composer.type(" ", true), "after our own space: no check")
        assertEquals(1, checks)

        composer.reset()
        boundaryBefore = true
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("the ")), composer.type(" ", true), "a space before the moved cursor")
        assertEquals(2, checks)

        composer.newField(atStart = true)
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("the ")), composer.type(" ", true), "the field start needs no check")
        assertEquals(2, checks)
    }

    @Test
    fun finishKeepsTheWordAsTypedAndResetForgetsIt() {
        typeAll("teh")
        assertEquals(listOf(Edit.FinishComposing), composer.finish())
        assertEquals("", composer.current)
        assertTrue(composer.finish().isEmpty())

        typeAll("abc")
        composer.reset()
        assertEquals("", composer.current)
        assertEquals(listOf(Edit.Commit(" ")), composer.type(" ", true))
    }

    @Test
    fun aPickedSuggestionReplacesTheWordWithASpace() {
        typeAll("tomo")
        assertEquals(listOf(Edit.Commit("tomorrow ")), composer.pick("tomorrow", keep = false))
        assertEquals("", composer.current)
        assertTrue(composer.pick("x", keep = false).isEmpty(), "nothing to replace between words")
        // After a pick the next word stands on its own, so it may be corrected.
        typeAll("teh")
        assertTrue(composer.mayCorrect)
        assertEquals(listOf(Edit.Commit("the ")), composer.type(" ", true))
    }

    @Test
    fun keepingTheTypedWordStopsItBeingCorrectedInThisField() {
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("teh ")), composer.pick("teh", keep = true))
        assertEquals(setOf("teh"), composer.revertedWords)
        typeAll("teh")
        assertEquals(listOf(Edit.Commit("teh ")), composer.type(" ", true))
    }

    @Test
    fun aSwipedWordGetsASpaceOnlyWhereItNeedsOne() {
        composer.newField(atStart = true)
        assertEquals(listOf(Edit.Compose("hello")), composer.swipe("hello"), "field start")
        assertEquals(listOf(Edit.Commit("hello "), Edit.Compose("world")), composer.swipe("world"), "after a swipe")
        composer.type(" ", true)
        assertEquals(listOf(Edit.Compose("again")), composer.swipe("again"), "after our own space")
        composer.type("@", true)
        assertEquals(listOf(Edit.Compose("name")), composer.swipe("name"), "after an opening character")
        composer.type(".", true)
        assertEquals(listOf(Edit.Commit(" "), Edit.Compose("so")), composer.swipe("so"), "after a full stop")
    }

    @Test
    fun aSwipeAfterTypedLettersKeepsThemAsAWord() {
        composer.newField(atStart = true)
        typeAll("ok")
        assertEquals(listOf(Edit.Commit("ok "), Edit.Compose("thanks")), composer.swipe("thanks"))
    }

    @Test
    fun whereTheCursorWasMovedTheCharacterBeforeDecidesTheSpace() {
        composer.reset()
        charBeforeCursor = 'k'
        assertEquals(listOf(Edit.Commit(" "), Edit.Compose("yes")), composer.swipe("yes"))
        composer.reset()
        charBeforeCursor = null
        assertEquals(listOf(Edit.Compose("yes")), composer.swipe("yes"), "the field start")
    }

    @Test
    fun backspaceRemovesASwipedWordWholeButTypedLettersOneByOne() {
        composer.newField(atStart = true)
        composer.swipe("hello")
        assertEquals(listOf(Edit.Compose("")), composer.backspace())
        composer.swipe("hello")
        composer.type("s", true)
        assertEquals(listOf(Edit.Compose("hello")), composer.backspace(), "typed after the swipe: one letter")
    }

    @Test
    fun aSwipedWordEndsLikeATypedOneAndIsNotCorrected() {
        composer.newField(atStart = true)
        composer.swipe("teh")
        assertEquals(listOf(Edit.Commit("the ")), composer.type(" ", true), "the corrector decides, as for typing")
    }

    @Test
    fun wordsKeptAsTypedReachTheLearner() {
        composer.newField(atStart = true)
        typeAll("kain ")
        typeAll("sana.")
        typeAll(" tayo")
        composer.finish()
        assertEquals(listOf("kain" to false, "sana" to false, "tayo" to false), learned)
    }

    @Test
    fun correctedWordsAreNotLearnedButUndoingOrKeepingThemIs() {
        composer.newField(atStart = true)
        typeAll("teh ")
        assertEquals(emptyList(), learned, "autocorrect changed it")
        composer.backspace()
        assertEquals(listOf("teh" to true), learned)

        composer.newField(atStart = true)
        learned.clear()
        typeAll("Teh")
        composer.pick("Teh", keep = true)
        composer.pick("x", keep = false)
        assertEquals(listOf("Teh" to true), learned)
    }

    @Test
    fun swipedWordsAndWordsThatDontStandAloneAreNotLearned() {
        composer.newField(atStart = true)
        composer.swipe("hello")
        composer.type(" ", true)
        composer.swipe("world")
        composer.finish()
        typeAll("john@")
        typeAll("gmail ")
        charBeforeCursor = '#'
        composer.reset()
        typeAll("tag ")
        assertEquals(emptyList(), learned)
    }
}
