package app.kisscut.keys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypingLayoutTest {
    private fun texts(row: List<Key>) = row.filterIsInstance<Key.Text>().joinToString("") { it.text }

    @Test
    fun lettersAreQwertyWithShiftBackspaceAndBottomRow() {
        val rows = typingRows(TypingPage.LETTERS)
        assertEquals(listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", ",."), rows.map(::texts))
        assertEquals(Key.Shift, rows[2].first())
        assertEquals(Key.Backspace, rows[2].last())
        assertEquals(
            listOf(Key.Page(TypingPage.SYMBOLS), Key.Stickers, Key.Globe, Key.Text(","), Key.Space, Key.Text("."), Key.Enter),
            rows[3],
        )
    }

    @Test
    fun numberRowAddsDigitsAboveTheLettersOnly() {
        val rows = typingRows(TypingPage.LETTERS, numberRow = true)
        assertEquals(listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm", ",."), rows.map(::texts))
        assertEquals(4, typingRows(TypingPage.SYMBOLS, numberRow = true).size)
    }

    @Test
    fun panelHeightFollowsTheSettings() {
        val normal = panelMiddleHeight(screenPx = 2000, chromePx = 300, minPx = 200, scale = 1f, numberRow = false)
        assertEquals(500, normal)
        assertEquals(425, panelMiddleHeight(2000, 300, 200, scale = 0.85f, numberRow = false))
        assertEquals(575, panelMiddleHeight(2000, 300, 200, scale = 1.15f, numberRow = false))
        // One more key row: a quarter of the whole panel (middle plus chrome) on top.
        assertEquals(500 + 200, panelMiddleHeight(2000, 300, 200, scale = 1f, numberRow = true))
        assertEquals(200, panelMiddleHeight(400, 300, 200, scale = 0.85f, numberRow = false))
    }

    @Test
    fun symbolPagesLinkBackToLetters() {
        val symbols = typingRows(TypingPage.SYMBOLS)
        assertEquals("1234567890", texts(symbols[0]))
        assertEquals(Key.Page(TypingPage.MORE_SYMBOLS), symbols[2].first())
        assertEquals(Key.Page(TypingPage.LETTERS), symbols[3].first())
        val more = typingRows(TypingPage.MORE_SYMBOLS)
        assertEquals(Key.Page(TypingPage.SYMBOLS), more[2].first())
        assertEquals(Key.Page(TypingPage.LETTERS), more[3].first())
    }

    @Test
    fun everyPageHasStickersGlobeSpaceEnterAndBackspace() {
        for (page in TypingPage.entries) {
            val keys = typingRows(page).flatten()
            for (k in listOf(Key.Stickers, Key.Globe, Key.Space, Key.Enter, Key.Backspace)) {
                assertTrue(k in keys, "$page lacks $k")
            }
        }
    }

    @Test
    fun longPressLettersOfferAccentsMostCommonFirst() {
        assertEquals(listOf("é", "è", "ê", "ë", "ē"), alternates("e", upper = false))
        assertEquals(listOf("ñ", "ń"), alternates("n", upper = false))
        assertTrue(alternates("q", upper = false).isEmpty())
        for (letter in "eaouincsyz") assertTrue(alternates(letter.toString(), upper = false).isNotEmpty(), "$letter")
    }

    @Test
    fun accentsFollowShiftButNeverGrowLonger() {
        assertEquals(listOf("É", "È", "Ê", "Ë", "Ē"), alternates("e", upper = true))
        // ß has no one-letter capital ("SS"), so it stays as it is.
        assertEquals(listOf("ß", "Ś", "Š"), alternates("s", upper = true))
    }

    @Test
    fun longPressPeriodOffersPunctuation() {
        val marks = alternates(".", upper = true)
        assertTrue(marks.containsAll(listOf("?", "!", "'", "-")))
        assertTrue(marks.all { it.length == 1 && !it[0].isLetter() })
    }

    @Test
    fun slidingPicksTheAlternateUnderTheFinger() {
        assertEquals(0, alternateIndex(fingerX = -40f, popupLeft = 0f, cellWidth = 100f, count = 4))
        assertEquals(0, alternateIndex(fingerX = 50f, popupLeft = 0f, cellWidth = 100f, count = 4))
        assertEquals(2, alternateIndex(fingerX = 260f, popupLeft = 0f, cellWidth = 100f, count = 4))
        assertEquals(3, alternateIndex(fingerX = 900f, popupLeft = 0f, cellWidth = 100f, count = 4))
        assertEquals(1, alternateIndex(fingerX = 370f, popupLeft = 250f, cellWidth = 100f, count = 4))
    }

    @Test
    fun popupsStayInsideTheKeyboard() {
        assertEquals(150, popupLeft(keyCenterX = 200, popupWidth = 100, containerWidth = 1000))
        assertEquals(0, popupLeft(keyCenterX = 20, popupWidth = 300, containerWidth = 1000))
        assertEquals(700, popupLeft(keyCenterX = 990, popupWidth = 300, containerWidth = 1000))
        assertEquals(0, popupLeft(keyCenterX = 50, popupWidth = 1200, containerWidth = 1000))
    }

    @Test
    fun spaceSlideStartsPastTheSlopThenMovesOneCharacterPerStep() {
        val slide = CursorSlide(startSlop = 20f, step = 10f)
        slide.down(100f)
        assertEquals(0, slide.move(115f))
        assertFalse(slide.sliding, "a small wobble is still a tap")
        assertEquals(0, slide.move(125f))
        assertTrue(slide.sliding)
        assertEquals(1, slide.move(130f))
        assertEquals(2, slide.move(152f))
        assertEquals(-3, slide.move(120f))
    }

    @Test
    fun spaceSlideWorksLeftAndResetsOnTheNextPress() {
        val slide = CursorSlide(startSlop = 20f, step = 10f)
        slide.down(100f)
        assertEquals(-2, slide.move(55f))
        slide.down(300f)
        assertFalse(slide.sliding)
        assertEquals(0, slide.move(305f))
    }

    @Test
    fun shiftOnceThenLockThenOff() {
        val s = ShiftLogic()
        s.tap(now = 0)
        assertEquals(ShiftState.ONCE, s.state)
        assertEquals("A", s.apply("a"))
        s.typed()
        assertEquals(ShiftState.OFF, s.state)

        s.tap(now = 1_000)
        s.tap(now = 1_200)
        assertEquals(ShiftState.LOCKED, s.state)
        s.typed()
        assertEquals(ShiftState.LOCKED, s.state)
        s.tap(now = 5_000)
        assertEquals(ShiftState.OFF, s.state)
    }

    @Test
    fun slowSecondTapTurnsShiftOff() {
        val s = ShiftLogic()
        s.tap(now = 0)
        s.tap(now = 2_000)
        assertEquals(ShiftState.OFF, s.state)
    }

    @Test
    fun autoCapNeverBreaksCapsLock() {
        val s = ShiftLogic()
        s.autoCap(true)
        assertEquals(ShiftState.ONCE, s.state)
        s.autoCap(false)
        assertEquals(ShiftState.OFF, s.state)
        s.tap(0)
        s.tap(100)
        s.autoCap(false)
        assertEquals(ShiftState.LOCKED, s.state)
    }

    @Test
    fun enterRunsTheFieldActionOrTypesANewLine() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(EnterAction.Action(EditorInfo.IME_ACTION_SEND), enterAction(EditorInfo.IME_ACTION_SEND, text))
        assertEquals(EnterAction.Action(EditorInfo.IME_ACTION_SEARCH), enterAction(EditorInfo.IME_ACTION_SEARCH, text))
        assertEquals(EnterAction.NewLine, enterAction(EditorInfo.IME_ACTION_UNSPECIFIED, text))
        assertEquals(EnterAction.NewLine, enterAction(EditorInfo.IME_ACTION_NONE, text))
        assertEquals(
            EnterAction.NewLine,
            enterAction(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION, text),
        )
        assertEquals(
            EnterAction.NewLine,
            enterAction(EditorInfo.IME_ACTION_SEND, text or InputType.TYPE_TEXT_FLAG_MULTI_LINE),
        )
    }

    @Test
    fun fieldsOpenOnTheRightView() {
        val text = InputType.TYPE_CLASS_TEXT
        assertEquals(FieldStart(KeyboardView.STICKERS, TypingPage.LETTERS, true), fieldStart(text, KeyboardView.STICKERS))
        assertEquals(FieldStart(KeyboardView.LETTERS, TypingPage.LETTERS, true), fieldStart(text, KeyboardView.LETTERS))
        assertEquals(
            FieldStart(KeyboardView.LETTERS, TypingPage.SYMBOLS, true),
            fieldStart(InputType.TYPE_CLASS_PHONE, KeyboardView.STICKERS),
        )
        val password = fieldStart(text or InputType.TYPE_TEXT_VARIATION_PASSWORD, KeyboardView.STICKERS)
        assertEquals(KeyboardView.LETTERS, password.view)
        assertFalse(password.stickersAllowed)
        val pin = fieldStart(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, KeyboardView.LETTERS)
        assertEquals(TypingPage.SYMBOLS, pin.page)
        assertFalse(pin.stickersAllowed)
    }
}
