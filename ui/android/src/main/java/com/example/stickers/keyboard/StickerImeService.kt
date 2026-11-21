package com.example.stickers.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.VisibleForTesting
import androidx.core.view.WindowCompat
import com.example.stickers.App
import com.example.stickers.MainActivity
import com.example.stickers.R
import com.example.stickers.shared.core.KeyboardTab
import com.example.stickers.shared.core.StickerKind
import com.example.stickers.shared.db.Sticker
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
    private var inserter: StickerInserter? = null
    private var password = false

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
        val palette = KeyboardPalette.of(this)
        val panel = StickerPanelView(this, palette, container::thumbnailOf, Actions(model))
        this.panel = panel
        // Dark system buttons under a light panel, light ones under a dark panel.
        window?.window?.let { WindowCompat.getInsetsController(it, panel).isAppearanceLightNavigationBars = !palette.dark }
        scope.launch { model.state.collect { panel.render(it, password) } }
        return panel
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        password = info != null && isPasswordField(info.inputType)
        val model = model ?: return
        panel?.render(model.state.value, password)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        panel?.clearHint()
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

    private inner class Actions(private val model: KeyboardModel) : StickerPanelView.Actions {
        override fun onTab(tab: KeyboardTab) = model.selectTab(tab)
        override fun onChip(id: Long?) = model.selectChip(id)
        override fun onKind(kind: StickerKind) = model.selectKind(kind)
        override fun onSticker(sticker: Sticker) = send(sticker)
        override fun onGlobe() = switchBack()
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
