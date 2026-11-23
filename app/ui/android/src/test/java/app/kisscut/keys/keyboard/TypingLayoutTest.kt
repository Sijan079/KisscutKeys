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
