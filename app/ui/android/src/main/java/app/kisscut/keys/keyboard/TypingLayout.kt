package app.kisscut.keys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** One key on the typing keyboard (docs/KEYBOARD.md, Typing layout). [weight] is its width in key units. */
sealed class Key(val weight: Float) {
    /** Types [text]; letters follow Shift. */
    data class Text(val text: String, val w: Float = 1f) : Key(w)
    data object Shift : Key(1.5f)
    data object Backspace : Key(1.5f)
    data object Space : Key(5f)
    data object Enter : Key(1.5f)
    data object Stickers : Key(1f)

    /** Switches to page [to]; the view labels it "?123", "ABC" or "=\<". */
    data class Page(val to: TypingPage, val w: Float = 1.5f) : Key(w)
}

enum class TypingPage { LETTERS, SYMBOLS, MORE_SYMBOLS }

/**
 * The rows of [page]: four, plus digits on top of the letters when [numberRow] is on. Letters are lower case;
 * the view applies Shift. With the [toolbar] on, the sticker key lives there instead of in the bottom row.
 */
fun typingRows(page: TypingPage, numberRow: Boolean = false, toolbar: Boolean = false): List<List<Key>> {
    fun row(chars: String) = chars.map { Key.Text(it.toString()) }
    val bottom = { pageKey: Key.Page ->
        listOfNotNull(pageKey, if (toolbar) null else Key.Stickers, Key.Text(","), Key.Space, Key.Text("."), Key.Enter)
    }
    return when (page) {
        TypingPage.LETTERS -> listOfNotNull(
            if (numberRow) row("1234567890") else null,
            row("qwertyuiop"),
            row("asdfghjkl"),
            listOf(Key.Shift) + row("zxcvbnm") + Key.Backspace,
            bottom(Key.Page(TypingPage.SYMBOLS)),
        )
        TypingPage.SYMBOLS -> listOf(
            row("1234567890"),
            row("@#\$_&-+()/"),
            listOf(Key.Page(TypingPage.MORE_SYMBOLS)) + row("*\"':;!?") + Key.Backspace,
            bottom(Key.Page(TypingPage.LETTERS)),
        )
        TypingPage.MORE_SYMBOLS -> listOf(
            row("~`|•√π÷×¶∆"),
            row("£¢€¥^°={}\\"),
            listOf(Key.Page(TypingPage.SYMBOLS)) + row("%©®™✓[]") + Key.Backspace,
            bottom(Key.Page(TypingPage.LETTERS)),
        )
    }
}

/** Long-press choices per key (docs/KEYBOARD.md, Key popups), most common first. */
private val ALTERNATES = mapOf(
    "e" to "éèêëē",
    "a" to "áàâäãåæā",
    "o" to "óòôöõøœō",
    "u" to "úùûüū",
    "i" to "íìîïī",
    "n" to "ñń",
    "c" to "çćč",
    "s" to "ßśš",
    "y" to "ýÿ",
    "z" to "žźż",
    "." to "?!'\"-:;@&",
)

/** What a long-press on [key] offers. Letters follow [upper]; one that has no one-letter capital stays as it is. */
fun alternates(key: String, upper: Boolean): List<String> {
    val chars = ALTERNATES[key] ?: return emptyList()
    return chars.map { c ->
        val s = c.toString()
        val up = s.uppercase()
        if (upper && up.length == 1) up else s
    }
}

/** Which of [count] alternates, each [cellWidth] wide from [popupLeft], is under [fingerX]. */
fun alternateIndex(fingerX: Float, popupLeft: Float, cellWidth: Float, count: Int): Int =
    ((fingerX - popupLeft) / cellWidth).toInt().coerceIn(0, count - 1)

/** Left edge of a popup centered on a key, kept inside the keyboard's width. */
fun popupLeft(keyCenterX: Int, popupWidth: Int, containerWidth: Int): Int =
    (keyCenterX - popupWidth / 2).coerceAtMost(containerWidth - popupWidth).coerceAtLeast(0)

/**
 * Sliding on the space bar moves the cursor (docs/KEYBOARD.md, Cursor slide). A press that never moves past
 * [startSlop] is a tap and types a space; past it, every [step] pixels moves the cursor one character.
 */
class CursorSlide(private val startSlop: Float, private val step: Float) {
    var sliding = false
        private set
    private var anchor = 0f

    fun down(x: Float) {
        anchor = x
        sliding = false
    }

    /** Characters to move for the finger now at [x]: negative is left. */
    fun move(x: Float): Int {
        if (!sliding) {
            val dx = x - anchor
            if (kotlin.math.abs(dx) < startSlop) return 0
            sliding = true
            anchor += if (dx > 0) startSlop else -startSlop
        }
        val steps = ((x - anchor) / step).toInt()
        anchor += steps * step
        return steps
    }
}

/**
 * Height of the panel's middle part (between the tabs and the bottom row): about [share] of [screenPx] less
 * [chromePx], times [scale], never under [minPx]. The number row adds one more row of the same size, so the
 * letters keep their key size. The toolbar adds its own height, [toolbarPx], on top.
 */
fun panelMiddleHeight(
    screenPx: Int,
    chromePx: Int,
    minPx: Int,
    scale: Float,
    numberRow: Boolean,
    share: Float = 0.40f,
    toolbarPx: Int = 0,
): Int {
    val base = maxOf(minPx, ((screenPx * share - chromePx) * scale).toInt())
    return (if (numberRow) base + (base + chromePx) / 4 else base) + toolbarPx
}

/** Height of the optional toolbar row above the letters (Settings, Toolbar). */
const val TOOLBAR_DP = 48

enum class ShiftState { OFF, ONCE, LOCKED }

/**
 * Shift and auto-capitals. Tap Shift for one capital, tap again within [DOUBLE_TAP_MS] for caps lock,
 * tap once more to turn it off. Auto-capitals never override caps lock.
 */
class ShiftLogic {
    var state = ShiftState.OFF
        private set
    private var lastTap = Long.MIN_VALUE / 2

    fun tap(now: Long) {
        val quick = now - lastTap <= DOUBLE_TAP_MS
        lastTap = now
        state = when (state) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> if (quick) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
    }

    /** After a letter is typed: a one-shot capital is used up. */
    fun typed() {
        if (state == ShiftState.ONCE) state = ShiftState.OFF
    }

    /** [capsWanted] comes from the field's cursor caps mode (start of a sentence). */
    fun autoCap(capsWanted: Boolean) {
        if (state != ShiftState.LOCKED) state = if (capsWanted) ShiftState.ONCE else ShiftState.OFF
    }

    fun apply(letter: String): String = if (state == ShiftState.OFF) letter else letter.uppercase()

    private companion object {
        const val DOUBLE_TAP_MS = 400L
    }
}

/** What Enter does: type a new line, or run the field's action (Send, Search, Go, Done, Next...). */
sealed interface EnterAction {
    data object NewLine : EnterAction
    data class Action(val id: Int) : EnterAction
}

fun enterAction(imeOptions: Int, inputType: Int): EnterAction {
    if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterAction.NewLine
    val multiLine = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
        inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
    if (multiLine) return EnterAction.NewLine
    val action = imeOptions and EditorInfo.IME_MASK_ACTION
    if (action == EditorInfo.IME_ACTION_UNSPECIFIED || action == EditorInfo.IME_ACTION_NONE) return EnterAction.NewLine
    return EnterAction.Action(action)
}

/** Letters or the sticker panel. */
enum class KeyboardView { LETTERS, STICKERS }

/** Which page and view a field opens on (docs/KEYBOARD.md, Typing layout). */
data class FieldStart(val view: KeyboardView, val page: TypingPage, val stickersAllowed: Boolean)

fun fieldStart(inputType: Int, lastView: KeyboardView): FieldStart {
    val numeric = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
        else -> false
    }
    val password = isPasswordField(inputType)
    return when {
        password -> FieldStart(KeyboardView.LETTERS, if (numeric) TypingPage.SYMBOLS else TypingPage.LETTERS, false)
        numeric -> FieldStart(KeyboardView.LETTERS, TypingPage.SYMBOLS, true)
        else -> FieldStart(lastView, TypingPage.LETTERS, true)
    }
}
