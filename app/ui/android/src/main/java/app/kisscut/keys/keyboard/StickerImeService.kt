package app.kisscut.keys.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.core.view.WindowCompat
import app.kisscut.keys.App
import app.kisscut.keys.MainActivity
import app.kisscut.keys.R
import app.kisscut.keys.debug.FieldTypesLog
import app.kisscut.keys.shared.core.KeyboardTab
import app.kisscut.keys.shared.core.StickerKind
import app.kisscut.keys.shared.db.Sticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * The sticker keyboard (docs/KEYBOARD.md). Reads the app's one database through [App.container]; all
 * coroutines live in a scope that is cancelled when the panel is rebuilt or the service is destroyed.
 * It never reads what the user types: the only text action is Backspace, sent as a key event.
 */
class StickerImeService : InputMethodService() {
    private var scope: CoroutineScope? = null
    private var model: KeyboardModel? = null
    private var panel: StickerPanelView? = null
    private var typing: TypingView? = null
    private var inserter: StickerInserter? = null
    private var password = false

    /** Whether the views were built dark; rebuilt when the Settings theme changed since. */
    private var builtDark: Boolean? = null

    /** Letters or stickers; kept while the keyboard runs, so it reopens on the view used last. */
    private var view = KeyboardView.LETTERS

    /** Last result of a sticker tap (tests read it). */
    @VisibleForTesting
    internal var lastOutcome: InsertOutcome? = null

    override fun onCreate() {
        super.onCreate()
        active = WeakReference(this)
    }

    override fun onCreateInputView(): View {
        // Called again after a configuration change (rotation, dark mode): start fresh.
        scope?.cancel()
        val scope = MainScope().also { this.scope = it }
        val container = (application as App).container
        val model = KeyboardModel(container.libraryBackend, scope).also { this.model = it }
        inserter = StickerInserter(this, container::originalOf, container.stickers::markUsed)
        val palette = KeyboardPalette.of(this, container.theme.value)
        builtDark = palette.dark
        val panel = StickerPanelView(this, palette, container::thumbnailOf, Actions(model))
        val typing = TypingView(this, palette, Keys())
        this.panel = panel
        this.typing = typing
        // The sticker panel sets the height; the letters fill the same space on top of it.
        val root = PanelFrame(this).apply {
            addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            addView(typing, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        // Dark system buttons under a light panel, light ones under a dark panel.
        window?.window?.let { WindowCompat.getInsetsController(it, root).isAppearanceLightNavigationBars = !palette.dark }
        scope.launch { model.state.collect { panel.render(it, password) } }
        show(view)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val theme = (application as App).container.theme.value
        if (builtDark != null && KeyboardPalette.of(this, theme).dark != builtDark) setInputView(onCreateInputView())
        info?.let(FieldTypesLog::log)
        password = info != null && isPasswordField(info.inputType)
        val start = fieldStart(info?.inputType ?: 0, view)
        typing?.apply {
            showPage(start.page)
            setEnter(enterAction(info?.imeOptions ?: 0, info?.inputType ?: 0))
            setStickersAllowed(start.stickersAllowed)
        }
        show(start.view)
        updateAutoCaps()
        val model = model ?: return
        panel?.render(model.state.value, password)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps()
    }

    /** Shows letters or stickers. The hidden one stays laid out (INVISIBLE) so the height never jumps. */
    private fun show(target: KeyboardView) {
        view = target
        typing?.visibility = if (target == KeyboardView.LETTERS) View.VISIBLE else View.INVISIBLE
        panel?.visibility = if (target == KeyboardView.STICKERS) View.VISIBLE else View.INVISIBLE
        if (target == KeyboardView.LETTERS) panel?.clearHint()
    }

    /**
     * Capital at the start of a sentence when the field asks for it. Reads only the cursor's caps mode,
     * never the text.
     */
    private fun updateAutoCaps() {
        val typing = typing ?: return
        if (typing.page != TypingPage.LETTERS) return
        val info = currentInputEditorInfo
        val wanted = info != null && info.inputType != 0 && !password &&
            (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
        typing.shift.autoCap(wanted)
        typing.refreshShift()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        panel?.clearHint()
        typing?.dismissPopups()
    }

    /** Never take over the screen in landscape: the chat must stay visible. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        if (active?.get() === this) active = null
        super.onDestroy()
    }

    private fun send(sticker: Sticker) {
        val scope = scope ?: return
        val inserter = inserter ?: return
        scope.launch {
            val outcome = inserter.insert(currentInputConnection, currentInputEditorInfo, sticker)
            lastOutcome = outcome
            val hint = when (outcome) {
                InsertOutcome.SENT -> null
                InsertOutcome.COPIED -> R.string.kb_hint_copied
                InsertOutcome.COPIED_TYPE_REJECTED -> R.string.kb_hint_type_rejected
                InsertOutcome.BLOCKED -> R.string.kb_password
                InsertOutcome.FAILED -> R.string.kb_hint_failed
            }
            if (hint != null) panel?.showHint(getString(hint)) else panel?.clearHint()
        }
    }

    private fun switchBack() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            val token = window?.window?.attributes?.token
            @Suppress("DEPRECATION")
            token != null && getSystemService(InputMethodManager::class.java).switchToLastInputMethod(token)
        }
        if (!switched) showPicker()
    }

    private fun showPicker() = getSystemService(InputMethodManager::class.java).showInputMethodPicker()

    /** Key presses from the letters. Text goes straight to the field; nothing is kept. */
    private inner class Keys : TypingView.Actions {
        override fun onText(text: String) {
            currentInputConnection?.commitText(text, 1)
            typing?.shift?.typed()
            updateAutoCaps()
        }

        override fun onBackspace() {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        /** Arrow key events, like a hardware keyboard: the field moves its own cursor; nothing is read. */
        override fun onMoveCursor(chars: Int) {
            val key = if (chars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            repeat(kotlin.math.abs(chars)) { sendDownUpKeyEvents(key) }
        }

        override fun onEnter() {
            val info = currentInputEditorInfo
            when (val action = enterAction(info?.imeOptions ?: 0, info?.inputType ?: 0)) {
                is EnterAction.Action -> currentInputConnection?.performEditorAction(action.id)
                EnterAction.NewLine -> currentInputConnection?.commitText("\n", 1)
            }
            updateAutoCaps()
        }

        override fun onStickers() {
            if (!password) show(KeyboardView.STICKERS)
        }

        override fun onGlobe() = switchBack()
        override fun onGlobeLongPress() = showPicker()
    }

    private inner class Actions(private val model: KeyboardModel) : StickerPanelView.Actions {
        override fun onTab(tab: KeyboardTab) = model.selectTab(tab)
        override fun onChip(id: Long?) = model.selectChip(id)
        override fun onKind(kind: StickerKind) = model.selectKind(kind)
        override fun onSticker(sticker: Sticker) = send(sticker)
        override fun onLetters() = show(KeyboardView.LETTERS)
        override fun onGlobeLongPress() = showPicker()
        override fun onBackspace() = sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        override fun onOpenApp() {
            startActivity(Intent(this@StickerImeService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    internal companion object {
        /** The running keyboard, for instrumented tests. */
        @VisibleForTesting
        @Volatile
        var active: WeakReference<StickerImeService>? = null

        @VisibleForTesting
        internal val StickerImeService.panelForTest: StickerPanelView? get() = panel

        @VisibleForTesting
        internal val StickerImeService.typingForTest: TypingView? get() = typing

        @VisibleForTesting
        internal val StickerImeService.viewForTest: KeyboardView get() = view

        @VisibleForTesting
        internal val StickerImeService.loadedForTest: Boolean get() = model?.state?.value?.loaded == true

        @VisibleForTesting
        internal val StickerImeService.inserterForTest: StickerInserter? get() = inserter

        /** Rebuilds the panel, so it reads a library the test just swapped in. Main thread. */
        @VisibleForTesting
        internal fun StickerImeService.rebuildForTest() {
            lastOutcome = null
            setInputView(onCreateInputView())
            onStartInputView(currentInputEditorInfo, false)
        }
    }
}

/** Holds both views at the sticker panel's height, so the letters never grow past it. */
private class PanelFrame(context: android.content.Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val panel = getChildAt(0)
        measureChild(panel, widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(panel.measuredHeight, MeasureSpec.EXACTLY))
    }
}
