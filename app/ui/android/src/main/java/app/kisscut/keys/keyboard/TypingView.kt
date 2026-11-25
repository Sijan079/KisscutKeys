package app.kisscut.keys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.kisscut.keys.R

/**
 * The typing keyboard (docs/KEYBOARD.md, Typing layout): letters, `?123` and `=\<` pages, Shift with caps lock,
 * Backspace that repeats while held, an Enter key that shows the field's action and a sticker key.
 * Holding Space opens the keyboard list. With [topRow] on, a row above the keys shows suggestions while a word is
 * being typed and, between words, the toolbar (Stickers, Emoji, Settings and Switch keyboard) if [toolbar] is on,
 * else nothing; the row never changes height. With the toolbar, the sticker key leaves the bottom row.
 * It only reports key presses through [actions]; it never sees the field's text.
 */
@SuppressLint("ViewConstructor")
class TypingView(
    context: Context,
    private val palette: KeyboardPalette,
    private val actions: Actions,
    private val feedback: KeyFeedback,
    private val numberRow: Boolean = false,
    private val toolbar: Boolean = false,
    private val topRow: Boolean = toolbar,
) : LinearLayout(context) {

    interface Actions {
        fun onText(text: String)
        fun onBackspace()

        /** Moves the cursor [chars] characters; negative is left. */
        fun onMoveCursor(chars: Int)
        fun onEnter()
        fun onStickers()

        /** Opens the system keyboard list (hold Space, or the toolbar). */
        fun onSwitchKeyboard()

        /** Toolbar: the sticker panel on its Emoji tab. */
        fun onEmoji()

        /** Toolbar: the app's Settings screen. */
        fun onSettings()

        /** A tapped suggestion; [keep] when it is the word as typed, chosen over a correction. */
        fun onSuggestion(word: String, keep: Boolean)
    }

    val shift = ShiftLogic()
    var page: TypingPage = TypingPage.LETTERS
        private set
    private var enter: EnterAction = EnterAction.NewLine
    private var enterActionId = EditorInfo.IME_ACTION_UNSPECIFIED
    private var stickersAllowed = true
    private val letterLabels = mutableListOf<Pair<TextView, String>>()
    private var shiftIcon: ImageView? = null
    private val popups = KeyPopups(context, palette, this)
    private var toolbarIcons: View? = null
    private var strip: LinearLayout? = null
    private var slots: List<StripSlot?> = emptyList()

    init {
        orientation = VERTICAL
        background = panelBackground(context, palette)
        setPadding(dp(4), dp(10), dp(4), dp(8))
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(dp(4), dp(10), dp(4), dp(8) + nav)
            insets
        }
        build()
    }

    fun showPage(to: TypingPage) {
        if (to == page) return
        page = to
        build()
    }

    /** Sets the Enter key for the current field. */
    fun setEnter(action: EnterAction) {
        enter = action
        enterActionId = (action as? EnterAction.Action)?.id ?: EditorInfo.IME_ACTION_UNSPECIFIED
        build()
    }

    /** Off in password fields: the sticker key is dimmed and does nothing. */
    fun setStickersAllowed(allowed: Boolean) {
        if (stickersAllowed == allowed) return
        stickersAllowed = allowed
        build()
    }

    /** Call after Shift or auto-capitals change, to redraw letters and the Shift icon. */
    fun refreshShift() {
        for ((view, letter) in letterLabels) view.text = shift.apply(letter)
        shiftIcon?.let { icon ->
            icon.setImageResource(
                when (shift.state) {
                    ShiftState.OFF -> R.drawable.ic_kb_shift
                    ShiftState.ONCE -> R.drawable.ic_kb_shift_on
                    ShiftState.LOCKED -> R.drawable.ic_kb_shift_lock
                },
            )
            (icon.parent as View).contentDescription = context.getString(
                when (shift.state) {
                    ShiftState.OFF -> R.string.kb_key_shift
                    ShiftState.ONCE -> R.string.kb_key_shift_on
                    ShiftState.LOCKED -> R.string.kb_key_caps_lock
                },
            )
        }
    }

    /** Closes the key preview and alternates; call when the keyboard hides. */
    fun dismissPopups() = popups.dismissAll()

    override fun onDetachedFromWindow() {
        popups.dismissAll()
        super.onDetachedFromWindow()
    }

    private fun build() {
        popups.dismissAll()
        removeAllViews()
        letterLabels.clear()
        shiftIcon = null
        if (topRow) addView(topRowView(), LayoutParams(MATCH, dp(TOOLBAR_DP)))
        for (row in typingRows(page, numberRow, toolbar)) {
            val line = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }
            // The middle letter row is narrower: half a key of space on each side, as on most keyboards.
            val inset = page == TypingPage.LETTERS && row.size == 9
            if (inset) line.addView(View(context), LayoutParams(0, MATCH).apply { weight = 0.5f })
            for (key in row) line.addView(keyView(key), LayoutParams(0, MATCH).apply { weight = key.weight })
            if (inset) line.addView(View(context), LayoutParams(0, MATCH).apply { weight = 0.5f })
            addView(line, LayoutParams(MATCH, 0).apply { weight = 1f })
        }
        refreshShift()
        showSuggestions(slots)
    }

    /** The row above the keys: the toolbar icons (if on) under the suggestion strip, which covers them while shown. */
    private fun topRowView() = FrameLayout(context).apply {
        toolbarIcons = if (toolbar) toolbarRow().also { addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) } else null
        strip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = KeyboardTags.STRIP
            visibility = GONE
        }.also { addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) }
    }

    /** Shows [newSlots] (from [stripSlots]); an empty list shows the toolbar or a blank row. */
    fun showSuggestions(newSlots: List<StripSlot?>) {
        slots = newSlots
        val strip = strip ?: return
        strip.removeAllViews()
        if (newSlots.isEmpty()) {
            strip.visibility = GONE
            toolbarIcons?.visibility = VISIBLE
            return
        }
        toolbarIcons?.visibility = INVISIBLE
        strip.visibility = VISIBLE
        newSlots.forEachIndexed { i, slot ->
            if (i > 0) {
                strip.addView(
                    View(context).apply { setBackgroundColor(palette.line) },
                    LayoutParams(dp(1), dp(20)),
                )
            }
            strip.addView(slotView(i, slot), LayoutParams(0, MATCH).apply { weight = 1f })
        }
    }

    private fun slotView(index: Int, slot: StripSlot?) = FrameLayout(context).apply {
        tag = KeyboardTags.suggestion(index)
        if (slot == null) return@apply
        addView(
            TextView(context).apply {
                text = slot.label
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(if (slot.kind == SlotKind.KEEP) palette.muted else palette.text)
                applyFigtree(if (slot.kind == SlotKind.BEST || slot.kind == SlotKind.FIX) 800 else 600)
                if (slot.kind == SlotKind.KEEP) setTypeface(typeface, android.graphics.Typeface.ITALIC)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER).apply { setMargins(dp(6), 0, dp(6), 0) },
        )
        if (slot.kind == SlotKind.FIX) {
            addView(
                View(context).apply { background = rounded(palette.lime, dp(2f)) },
                FrameLayout.LayoutParams(dp(28), dp(3), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)
                    .apply { bottomMargin = dp(6) },
            )
        }
        contentDescription = if (slot.kind == SlotKind.KEEP) {
            context.getString(R.string.kb_suggestion_keep, slot.word)
        } else {
            slot.word
        }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), keyShape(palette.raised))
            addState(intArrayOf(), null)
        }
        isClickable = true
        isFocusable = true
        setOnClickListener {
            tap(it)
            actions.onSuggestion(slot.word, keep = slot.kind == SlotKind.KEEP)
        }
    }

    private fun keyView(key: Key): View = when (key) {
        is Key.Text -> {
            val letter = key.text.length == 1 && key.text[0].isLetter()
            textKey(key.text, special = false, tag = KeyboardTags.key(key.text)).also { cell ->
                if (letter) letterLabels += (cell.getChildAt(0) as TextView) to key.text
                val main = { if (letter) shift.apply(key.text) else key.text }
                // TalkBack and tests tap through the click; a finger goes through pressWithPopups.
                cell.setOnClickListener {
                    tap(it)
                    actions.onText(main())
                }
                pressWithPopups(cell, key.text, main)
            }
        }
        Key.Space -> textKey(context.getString(R.string.kb_key_space), special = false, tag = KeyboardTags.SPACE)
            .apply {
                (getChildAt(0) as TextView).textSize = 15f
                contentDescription = context.getString(R.string.kb_key_space)
                setOnClickListener {
                    tap(it)
                    actions.onText(" ")
                }
                ViewCompat.addAccessibilityAction(this, context.getString(R.string.kb_switch_keyboard)) { _, _ ->
                    actions.onSwitchKeyboard()
                    true
                }
                slideCursor(this)
            }
        is Key.Page -> {
            val label = when (key.to) {
                TypingPage.LETTERS -> R.string.kb_page_letters
                TypingPage.SYMBOLS -> R.string.kb_page_symbols
                TypingPage.MORE_SYMBOLS -> R.string.kb_page_more_symbols
            }
            textKey(context.getString(label), special = true, tag = KeyboardTags.page(key.to.name)).apply {
                (getChildAt(0) as TextView).textSize = 15f
                setOnClickListener {
                    tap(it)
                    showPage(key.to)
                }
            }
        }
        Key.Shift -> iconKey(R.drawable.ic_kb_shift, R.string.kb_key_shift, special = true, tag = KeyboardTags.SHIFT)
            .apply {
                shiftIcon = getChildAt(0) as ImageView
                if (page != TypingPage.LETTERS) visibility = INVISIBLE
                setOnClickListener {
                    tap(it)
                    shift.tap(System.currentTimeMillis())
                    refreshShift()
                }
            }
        Key.Backspace -> iconKey(R.drawable.ic_kb_backspace, R.string.kb_backspace, special = true, KeyboardTags.KEY_BACKSPACE)
            .apply { repeatWhileHeld(this) }
        Key.Enter -> {
            val (icon, label) = when (enterActionId) {
                EditorInfo.IME_ACTION_SEND -> R.drawable.ic_kb_send to R.string.kb_key_send
                EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_kb_search to R.string.kb_key_search
                EditorInfo.IME_ACTION_DONE -> R.drawable.ic_kb_done to R.string.kb_key_done
                EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_kb_next to R.string.kb_key_go
                else -> R.drawable.ic_kb_enter to R.string.kb_key_enter
            }
            iconKey(icon, label, special = true, tag = KeyboardTags.ENTER, fill = palette.lime, ink = palette.onLime)
                .apply {
                    setOnClickListener {
                        tap(it)
                        actions.onEnter()
                    }
                }
        }
        Key.Stickers -> iconKey(R.drawable.ic_kb_sticker, R.string.kb_key_stickers, special = true, KeyboardTags.STICKERS)
            .apply {
                isEnabled = stickersAllowed
                alpha = if (stickersAllowed) 1f else 0.4f
                setOnClickListener {
                    tap(it)
                    actions.onStickers()
                }
            }
    }

    /** Stickers and Emoji on the left, Settings and Switch keyboard on the right; flat icons, no key fill. */
    private fun toolbarRow() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        tag = KeyboardTags.TOOLBAR
        fun button(icon: Int, label: Int, testTag: String, enabled: Boolean = true, onTap: () -> Unit) =
            FrameLayout(context).apply {
                tag = testTag
                contentDescription = context.getString(label)
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), keyShape(palette.raised))
                    addState(intArrayOf(), null)
                }
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.4f
                isClickable = true
                isFocusable = true
                addView(
                    ImageView(context).apply {
                        setImageResource(icon)
                        imageTintList = ColorStateList.valueOf(palette.text)
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER),
                )
                setOnClickListener {
                    tap(it)
                    onTap()
                }
            }
        val stickers = button(
            R.drawable.ic_kb_sticker,
            R.string.kb_key_stickers,
            KeyboardTags.TOOLBAR_STICKERS,
            stickersAllowed,
        ) { actions.onStickers() }
        val emoji = button(R.drawable.ic_kb_emoji, R.string.kb_tab_emoji, KeyboardTags.TOOLBAR_EMOJI, stickersAllowed) {
            actions.onEmoji()
        }
        val settings = button(R.drawable.ic_kb_settings, R.string.kb_keyboard_settings, KeyboardTags.TOOLBAR_SETTINGS) {
            actions.onSettings()
        }
        val switch = button(R.drawable.ic_kb_keyboard, R.string.kb_switch_keyboard, KeyboardTags.TOOLBAR_SWITCH) {
            actions.onSwitchKeyboard()
        }
        for (b in listOf(stickers, emoji)) addView(b, LayoutParams(dp(TOOLBAR_DP), MATCH))
        addView(View(context), LayoutParams(0, MATCH).apply { weight = 1f })
        for (b in listOf(settings, switch)) addView(b, LayoutParams(dp(TOOLBAR_DP), MATCH))
    }

    /**
     * A finger on a text key: the preview shows on press and the key types on release. Holding a key that
     * has alternates opens them; sliding picks one and releasing types it. TalkBack gets each alternate as an
     * action on the key ("Type é").
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun pressWithPopups(cell: FrameLayout, key: String, main: () -> String) {
        val upper = { shift.state != ShiftState.OFF }
        val hasAlternates = alternates(key, upper = false).isNotEmpty()
        val open = Runnable { popups.showAlternates(cell, alternates(key, upper())) }
        cell.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    tap(v)
                    popups.showPreview(v, main())
                    if (hasAlternates) v.postDelayed(open, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> popups.moveFinger(e.x)
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    v.removeCallbacks(open)
                    val picked = popups.chosen
                    if (popups.alternatesShown) popups.dismissAll() else popups.hidePreview(v)
                    actions.onText(picked ?: main())
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.removeCallbacks(open)
                    popups.dismissAll()
                }
            }
            true
        }
        alternates(key, upper = false).forEachIndexed { i, option ->
            ViewCompat.addAccessibilityAction(cell, context.getString(R.string.kb_type_alternate, option)) { _, _ ->
                actions.onText(alternates(key, upper())[i])
                true
            }
        }
    }

    /**
     * Space: a tap types a space, a slide moves the cursor and types nothing, and holding still opens the
     * keyboard list and types nothing. TalkBack taps type a space; its actions menu has "Switch keyboard".
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun slideCursor(view: View) {
        val slide = CursorSlide(ViewConfiguration.get(context).scaledTouchSlop.toFloat(), dp(SLIDE_STEP_DP).toFloat())
        var held = false
        val hold = Runnable {
            held = true
            view.isPressed = false
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            actions.onSwitchKeyboard()
        }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    held = false
                    tap(v)
                    slide.down(e.x)
                    v.postDelayed(hold, SPACE_HOLD_MS)
                }
                MotionEvent.ACTION_MOVE -> if (!held) {
                    val chars = slide.move(e.x)
                    if (slide.sliding) v.removeCallbacks(hold)
                    if (chars != 0) {
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        actions.onMoveCursor(chars)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    v.removeCallbacks(hold)
                    if (!slide.sliding && !held) actions.onText(" ")
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.removeCallbacks(hold)
                }
            }
            true
        }
    }

    /** Deletes once on press, then repeats until the finger lifts. TalkBack taps delete once. */
    @SuppressLint("ClickableViewAccessibility")
    private fun repeatWhileHeld(view: View) {
        val repeat = object : Runnable {
            override fun run() {
                actions.onBackspace()
                view.postDelayed(this, REPEAT_MS)
            }
        }
        view.setOnClickListener { actions.onBackspace() }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    tap(v)
                    actions.onBackspace()
                    v.postDelayed(repeat, FIRST_REPEAT_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.removeCallbacks(repeat)
                }
            }
            true
        }
    }

    private fun textKey(label: String, special: Boolean, tag: String): FrameLayout = cell(special, tag).apply {
        addView(
            TextView(context).apply {
                text = label
                textSize = 21f
                setTextColor(palette.text)
                applyFigtree(600)
                gravity = Gravity.CENTER
                maxLines = 1
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER),
        )
        contentDescription = label
    }

    private fun iconKey(
        icon: Int,
        label: Int,
        special: Boolean,
        tag: String,
        fill: Int? = null,
        ink: Int = palette.text,
    ): FrameLayout = cell(special, tag, fill).apply {
        addView(
            ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(ink)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER),
        )
        contentDescription = context.getString(label)
    }

    /** A key: rounded fill with a small gap around it, darker while pressed. */
    private fun cell(special: Boolean, testTag: String, fill: Int? = null) = FrameLayout(context).apply {
        tag = testTag
        val base = fill ?: if (special) palette.line else palette.raised
        val pressed = if (special || fill != null) palette.raised else palette.line
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), keyShape(pressed))
            addState(intArrayOf(), keyShape(base))
        }
        isClickable = true
        isFocusable = true
        minimumHeight = dp(42)
    }

    private fun keyShape(color: Int): Drawable = InsetDrawable(rounded(color, dp(9f)), dp(3), dp(4), dp(3), dp(4))

    private fun tap(v: View) = feedback.tap(v)

    private fun dp(v: Int) = context.dp(v)
    private fun dp(v: Float) = context.dp(v)

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val FIRST_REPEAT_MS = 400L
        const val REPEAT_MS = 60L
        const val LONG_PRESS_MS = 350L

        /** Longer than a letter's accents: a slow tap on Space must still type a space. */
        const val SPACE_HOLD_MS = 500L
        const val SLIDE_STEP_DP = 12
    }
}
