package app.kisscut.keys.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
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
import app.kisscut.keys.settings.KeyboardHeight
import app.kisscut.keys.settings.KeyboardSettings
import app.kisscut.keys.debug.FieldTypesLog
import app.kisscut.keys.shared.core.KeyboardTab
import app.kisscut.keys.shared.core.StickerKind
import app.kisscut.keys.shared.core.words.Composer
import app.kisscut.keys.shared.core.words.Edit
import app.kisscut.keys.shared.core.words.Speller
import app.kisscut.keys.shared.core.words.WordList
import app.kisscut.keys.shared.db.Sticker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * The sticker keyboard (docs/KEYBOARD.md). Reads the app's one database through [App.container]; all
 * coroutines live in a scope that is cancelled when the panel is rebuilt or the service is destroyed.
 * The word being typed comes only from its own key presses ([Composer]), and the cursor is followed from the
 * lengths of its own edits ([CursorTracker]). The only thing it ever reads is the one character before the cursor
 * when a word starts where it didn't put the cursor, to see whether that word stands on its own (a space, a new
 * line or the field start); it keeps nothing. Nothing typed is stored, learned or sent.
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

    /** The settings the views were built with; rebuilt at the next field when they changed in Settings. */
    private data class Layout(
        val numberRow: Boolean,
        val height: KeyboardHeight,
        val toolbar: Boolean,
        val topRow: Boolean,
    )

    private var builtLayout: Layout? = null

    /** The running suggestion lookup, replaced on every keystroke. */
    private var stripJob: Job? = null

    /** Letters or stickers; kept while the keyboard runs, so it reopens on the view used last. */
    private var view = KeyboardView.LETTERS

    /** Loads the word list once per keyboard start, off the main thread (about 2.5 s on a mid-range phone). */
    private var loader: CoroutineScope? = null

    /** Null until the word list is loaded; typing works meanwhile, just without corrections. */
    @Volatile
    private var speller: Speller? = null

    private val composer = Composer(
        corrector = { typed, reverted -> speller?.autocorrect(typed, reverted) },
        startsAtBoundary = ::wordStartsHere,
    )
    private val cursor = CursorTracker()

    /** The underlined word and autocorrect run in this field (plain text, and the Settings allow it). */
    private var aids = false

    /** Last result of a sticker tap (tests read it). */
    @VisibleForTesting
    internal var lastOutcome: InsertOutcome? = null

    override fun onCreate() {
        super.onCreate()
        active = WeakReference(this)
        loader = MainScope().also { scope ->
            scope.launch { speller = withContext(Dispatchers.Default) { loadSpeller() } }
        }
    }

    private fun loadSpeller(): Speller {
        fun read(name: String) = assets.open("words/$name").bufferedReader().use { it.readText() }
        return Speller(listOf(WordList.parse(read("en_US.txt"), read("en_US_fixes.txt"))))
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
        val settings = container.keyboardSettings.value
        builtLayout = layoutOf(settings)
        // Sound and vibration are read on every tap, so they apply at once.
        val feedback = KeyFeedback(this) { container.keyboardSettings.value }
        val panel = StickerPanelView(
            this,
            palette,
            container::thumbnailOf,
            Actions(model),
            feedback,
            heightScale = settings.height.scale,
            numberRow = settings.numberRow,
            topRow = layoutOf(settings).topRow,
        )
        val typing = TypingView(
            this,
            palette,
            Keys(),
            feedback,
            numberRow = settings.numberRow,
            toolbar = settings.toolbar,
            topRow = layoutOf(settings).topRow,
        )
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
        val container = (application as App).container
        val dark = KeyboardPalette.of(this, container.theme.value).dark
        val layout = layoutOf(container.keyboardSettings.value)
        if (builtDark != null && (dark != builtDark || layout != builtLayout)) setInputView(onCreateInputView())
        info?.let(FieldTypesLog::log)
        password = info != null && isPasswordField(info.inputType)
        if (restarting) composer.reset() else composer.newField(atStart = info?.initialSelStart == 0)
        refreshStrip()
        cursor.forget(info?.initialSelStart ?: -1, info?.initialSelEnd ?: -1)
        aids = info != null && allowsTypingAids(info.inputType) && container.keyboardSettings.value.showsSuggestions
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
        if (aids) {
            val word = composer.current
            if (!cursor.onUpdate(newSelStart, newSelEnd, candidatesStart)) {
                // The user (or the app) moved the cursor: keep the word as it stands and forget the undo.
                if (word.isNotEmpty()) currentInputConnection?.finishComposingText()
                composer.reset()
                refreshStrip()
            } else if (word.isNotEmpty() && cursor.caughtUp && candidatesEnd - candidatesStart != word.length) {
                // The app ended or dropped the underlined word itself (some do on Send): let go of it too, or the
                // next letter would write the word again.
                composer.reset()
                cursor.forget(newSelStart, newSelEnd)
                refreshStrip()
            }
        }
        updateAutoCaps()
    }

    /** Shows letters or stickers. The hidden one stays laid out (INVISIBLE) so the height never jumps. */
    private fun show(target: KeyboardView) {
        if (target == KeyboardView.STICKERS) finishWord()
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
        finishWord()
        super.onFinishInputView(finishingInput)
        panel?.clearHint()
        typing?.dismissPopups()
    }

    /** Never take over the screen in landscape: the chat must stay visible. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        loader?.cancel()
        loader = null
        if (active?.get() === this) active = null
        super.onDestroy()
    }

    /**
     * Sends [edits] to the field as one batch. The cursor tracker hears about them first: the field's selection
     * update can arrive before this returns (always when the field is in this app's own process).
     */
    private fun apply(edits: List<Edit>) {
        if (edits.isEmpty()) return
        val ic = currentInputConnection ?: return
        val region = cursor.composingRegion
        cursor.applied(edits)
        ic.beginBatchEdit()
        // Re-mark our word first: some apps drop the underline without telling the keyboard, and replacing a word
        // that isn't marked any more would type it twice ("heyheya"). Positions come from our own edits, not reads.
        if (region != null && edits.first().let { it is Edit.Compose || it is Edit.Commit }) {
            ic.setComposingRegion(region.first, region.second)
        }
        for (edit in edits) {
            when (edit) {
                is Edit.Compose -> ic.setComposingText(edit.text, 1)
                is Edit.Commit -> ic.commitText(edit.text, 1)
                is Edit.DeleteBefore -> ic.deleteSurroundingText(edit.count, 0)
                Edit.FinishComposing -> ic.finishComposingText()
                Edit.DeleteKey -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
        ic.endBatchEdit()
        refreshStrip()
    }

    /**
     * Whether a word typed at the cursor stands on its own: the field starts there or a space or new line comes
     * before it. Reads that one character only, and only when a word starts where this keyboard didn't put the
     * cursor itself; nothing is kept.
     */
    private fun wordStartsHere(): Boolean {
        val before = currentInputConnection?.getTextBeforeCursor(1, 0) ?: return false
        return before.isEmpty() || before[0].isWhitespace()
    }

    /** Keeps the word being typed as it is (before Enter, stickers, emoji, a cursor slide or leaving the field). */
    private fun finishWord() {
        if (aids) apply(composer.finish())
    }

    /** Text this keyboard put in by other means (Enter, an emoji, a sticker): follow the next selection update. */
    private fun cursorMovedElsewhere() = cursor.forget()

    private fun send(sticker: Sticker) {
        val scope = scope ?: return
        val inserter = inserter ?: return
        cursorMovedElsewhere()
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

    private fun showPicker() = openPicker()

    /** Opens the system keyboard list; tests swap it so no system dialog covers the test screen. */
    @VisibleForTesting
    internal var openPicker: () -> Unit = { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }

    /**
     * Key presses from the letters. In plain text fields the word being typed is held by [composer] (only that word,
     * only until it ends); elsewhere text goes straight to the field. Nothing is kept.
     */
    private inner class Keys : TypingView.Actions {
        override fun onText(text: String) {
            if (aids) {
                val autocorrect = (application as App).container.keyboardSettings.value.autocorrect
                apply(composer.type(text, autocorrect))
            } else {
                currentInputConnection?.commitText(text, 1)
            }
            typing?.shift?.typed()
            updateAutoCaps()
        }

        override fun onBackspace() {
            if (aids) apply(composer.backspace()) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

        /** Arrow key events, like a hardware keyboard: the field moves its own cursor; nothing is read. */
        override fun onMoveCursor(chars: Int) {
            finishWord()
            cursorMovedElsewhere()
            val key = if (chars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            repeat(kotlin.math.abs(chars)) { sendDownUpKeyEvents(key) }
        }

        override fun onEnter() {
            finishWord()
            cursorMovedElsewhere()
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

        override fun onSwitchKeyboard() {
            finishWord()
            showPicker()
        }

        override fun onEmoji() {
            if (password) return
            model?.selectTab(KeyboardTab.Emoji)
            show(KeyboardView.STICKERS)
        }

        override fun onSuggestion(word: String, keep: Boolean) {
            apply(composer.pick(word, keep))
            typing?.shift?.typed()
            updateAutoCaps()
        }

        override fun onSettings() {
            finishWord()
            startActivity(
                Intent(this@StickerImeService, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
        }
    }

    private fun layoutOf(s: KeyboardSettings) =
        Layout(s.numberRow, s.height, s.toolbar, topRow = s.toolbar || s.showsSuggestions)

    /**
     * Updates the suggestion strip for the word being typed. The lookup (several ms on a mid-range phone) runs off
     * the main thread, so typing never waits; a result for a word that has changed since is dropped.
     */
    private fun refreshStrip() {
        stripJob?.cancel()
        val typing = typing ?: return
        val speller = speller
        val word = composer.current
        if (!aids || word.isEmpty() || speller == null) {
            typing.showSuggestions(emptyList())
            return
        }
        val autocorrect = (application as App).container.keyboardSettings.value.autocorrect && composer.mayCorrect
        val reverted = composer.revertedWords
        stripJob = scope?.launch {
            val slots = withContext(Dispatchers.Default) {
                val fix = if (autocorrect) speller.autocorrect(word, reverted)?.takeIf { it != word } else null
                stripSlots(word, speller.suggest(word), fix)
            }
            if (composer.current == word) typing.showSuggestions(slots)
        }
    }

    private inner class Actions(private val model: KeyboardModel) : StickerPanelView.Actions {
        override fun onTab(tab: KeyboardTab) = model.selectTab(tab)
        override fun onChip(id: Long?) = model.selectChip(id)
        override fun onKind(kind: StickerKind) = model.selectKind(kind)
        override fun onSticker(sticker: Sticker) = send(sticker)
        override fun onEmojiCategory(category: EmojiCategory) = model.selectEmojiCategory(category)

        /** Emoji go to the field as text, like a key; nothing is kept. */
        override fun onEmoji(emoji: String) {
            cursorMovedElsewhere()
            currentInputConnection?.commitText(emoji, 1)
        }
        override fun onLetters() = show(KeyboardView.LETTERS)
        override fun onSwitchKeyboard() = showPicker()
        override fun onBackspace() {
            cursorMovedElsewhere()
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }

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
        internal val StickerImeService.spellerLoadedForTest: Boolean get() = speller != null

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
