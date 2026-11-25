package app.kisscut.keys.keyboard

import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kisscut.keys.App
import app.kisscut.keys.AppContainer
import app.kisscut.keys.R
import app.kisscut.keys.debug.ImeTestActivity
import app.kisscut.keys.debug.SeedData
import app.kisscut.keys.settings.KeyboardHeight
import app.kisscut.keys.settings.KeyboardSettings
import app.kisscut.keys.settings.KeyboardSettingsStore
import app.kisscut.keys.keyboard.StickerImeService.Companion.inserterForTest
import app.kisscut.keys.keyboard.StickerImeService.Companion.loadedForTest
import app.kisscut.keys.keyboard.StickerImeService.Companion.panelForTest
import app.kisscut.keys.keyboard.StickerImeService.Companion.typingForTest
import app.kisscut.keys.keyboard.StickerImeService.Companion.viewForTest
import app.kisscut.keys.keyboard.StickerImeService.Companion.rebuildForTest
import app.kisscut.keys.keyboard.StickerImeService.Companion.spellerLoadedForTest
import app.kisscut.keys.shared.core.ByteSource
import app.kisscut.keys.shared.core.ImportResult
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerDatabase
import app.kisscut.keys.shared.db.buildStickerDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The real keyboard against [ImeTestActivity] (docs/TESTING.md, Keyboard). The IME is enabled and selected
 * with the `ime` shell command and reads an isolated library (in-memory database, `files/ime-test/`),
 * so the emulator's real library is never touched.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as App
    private lateinit var realContainer: AppContainer
    private lateinit var db: StickerDatabase
    private lateinit var dir: File
    private lateinit var container: AppContainer
    private lateinit var scenario: ActivityScenario<ImeTestActivity>
    private val keyboardSettings = MemoryKeyboardSettings()

    private lateinit var reactions: Sticker
    private lateinit var cat: Sticker
    private lateinit var webp: Sticker
    private lateinit var gif: Sticker
    private var reactionsId = 0L
    private var catsId = 0L
    private var workId = 0L

    @Before
    fun setUp() = runBlocking {
        realContainer = app.container
        db = Room.inMemoryDatabaseBuilder(app, StickerDatabase::class.java).buildStickerDatabase()
        dir = File(app.filesDir, "ime-test").apply { deleteRecursively(); mkdirs() }
        container = AppContainer(app, db, dir, keyboardSettings)
        app.container = container

        reactionsId = container.collections.create("Reactions", null).id
        catsId = container.collections.create("Cats", reactionsId).id
        workId = container.collections.create("Work", null).id
        reactions = add(SeedData.face(1, size = 96), reactionsId)
        cat = add(SeedData.face(2, size = 96), catsId)
        webp = add(SeedData.face(3, size = 96, webp = true), workId)
        gif = add(SeedData.blinkGif(0xFF9B8CFF.toInt(), size = 48), workId)

        ImeTestActivity.received.clear()
        scenario = ActivityScenario.launch(ImeTestActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        app.container = realContainer
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun tappedStickerIsCommittedAndCountsAsUsed() = runBlocking {
        focusStickers { it.anyImage }
        tapSticker(reactions)

        val got = waitFor { ImeTestActivity.received.firstOrNull() }
        assertEquals(ImeTestActivity.FIELD_ANY, got.field)
        // Stored as PNG or lossless WebP, whichever was smaller; the field takes any image, so it goes as stored.
        assertEquals(reactions.mimeType, got.mimeType)
        assertEquals(reactions.mimeType, got.resolvedType)
        waitFor { service().lastOutcome?.takeIf { it == InsertOutcome.SENT } }

        val used = waitFor { runBlocking { container.stickers.observeRecents().first() }.firstOrNull() }
        assertEquals(reactions.id, used.id)
        assertEquals(1, used.useCount)
    }

    @Test
    fun webpGoesAsPngWhereOnlyPngIsAccepted() {
        focusStickers { it.pngOnly }
        tab(KeyboardTags.collection(workId))
        tapSticker(webp)

        val got = waitFor { ImeTestActivity.received.firstOrNull() }
        assertEquals(ImeTestActivity.FIELD_PNG, got.field)
        assertEquals("image/png", got.mimeType)
        assertTrue(got.uri.toString().endsWith(".png"), got.uri.toString())
        // The stored file is untouched.
        assertTrue(container.originalOf(webp)!!.name.endsWith(".webp"))
    }

    @Test
    fun animatedStickerRejectedByFieldIsCopiedWithHint() {
        focusStickers { it.pngOnly }
        tab(KeyboardTags.collection(workId))
        tapSticker(gif)

        waitFor { service().lastOutcome?.takeIf { it == InsertOutcome.COPIED_TYPE_REJECTED } }
        assertEquals(app.getString(R.string.kb_hint_type_rejected), onMain { panel().hintText?.toString() })
        assertTrue(ImeTestActivity.received.isEmpty())
    }

    @Test
    fun fieldWithoutImagesGetsClipboardAndHint() {
        focusStickers { it.plain }
        tab(KeyboardTags.collection(reactionsId))
        tapSticker(reactions)

        waitFor { service().lastOutcome?.takeIf { it == InsertOutcome.COPIED } }
        assertEquals(app.getString(R.string.kb_hint_copied), onMain { panel().hintText?.toString() })
        // What went on the clipboard. Reading the clipboard back is not possible here: the emulator returns a
        // redacted text clip to the test.
        val clip = onMain { service().inserterForTest?.lastCopied } ?: fail("nothing copied")
        assertTrue(clip.description.hasMimeType("image/png"))
        assertTrue(clip.getItemAt(0).uri.toString().endsWith(".png"))
        assertEquals(app.packageName + ".fileprovider", clip.getItemAt(0).uri.authority)
        // Copying is not sending: no use recorded.
        assertNull(runBlocking { container.stickers.observeRecents().first() }.firstOrNull())
    }

    @Test
    fun webpOnTheClipboardIsAPngCopyThatOutlivesTheNextSend() {
        focusStickers { it.plain }
        tab(KeyboardTags.collection(workId))
        tapSticker(webp)

        waitFor { service().lastOutcome?.takeIf { it == InsertOutcome.COPIED } }
        val clip = onMain { service().inserterForTest?.lastCopied } ?: fail("nothing copied")
        assertTrue(clip.description.hasMimeType("image/png"), "pasting WebP fails in some apps")
        val copy = File(File(app.cacheDir, "send"), clip.getItemAt(0).uri.lastPathSegment!!)
        assertTrue(copy.isFile, copy.path)

        // A later send clears its own copies, but not the file the clipboard points to.
        focusStickers { it.pngOnly }
        tab(KeyboardTags.collection(workId))
        tapSticker(webp)
        waitFor { ImeTestActivity.received.firstOrNull() }
        assertTrue(copy.isFile, "clipboard copy still there")
        assertTrue(container.originalOf(webp)!!.name.endsWith(".webp"), "stored file untouched")
    }

    @Test
    fun passwordFieldOpensLettersWithStickersOff() {
        focus { it.password }
        assertEquals(KeyboardView.LETTERS, onMain { service().viewForTest })
        val stickerKey = onMain { service().typingForTest!!.findViewWithTag<View>(KeyboardTags.STICKERS) }
        assertTrue(!onMain { stickerKey.isEnabled })
        onMain { stickerKey.performClick() }
        assertEquals(KeyboardView.LETTERS, onMain { service().viewForTest })
        type(listOf("p", "w").map(::keyTag))
        waitFor { scenarioText { it.password }.takeIf { t -> t == "pw" } }
    }

    @Test
    fun autocorrectFixesOnSpaceAndBackspaceUndoesIt() {
        keyboardSettings.settings.value = KeyboardSettings(autocorrect = true)
        waitFor { container.keyboardSettings.value.takeIf { it.autocorrect } }
        focus { it.plain }
        waitFor { onMain { service().spellerLoadedForTest }.takeIf { it } }
        type(listOf("t", "e", "h", KeyboardTags.SPACE).map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "the " } }
        click(KeyboardTags.KEY_BACKSPACE, inTyping = true)
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "teh " } }
        // Undone once, never corrected again in this field.
        type(listOf("t", "e", "h", KeyboardTags.SPACE).map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "teh teh " } }
        // A known word is left alone, and Backspace then deletes normally.
        type(listOf("f", "o", "r", "m", KeyboardTags.SPACE).map(::keyTag))
        click(KeyboardTags.KEY_BACKSPACE, inTyping = true)
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "teh teh form" } }
    }

    @Test
    fun autocorrectIsOffByDefaultAndNeverInPasswords() {
        focus { it.plain }
        waitFor { onMain { service().spellerLoadedForTest }.takeIf { it } }
        type(listOf("t", "e", "h", KeyboardTags.SPACE).map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "teh " } }

        keyboardSettings.settings.value = KeyboardSettings(autocorrect = true)
        waitFor { container.keyboardSettings.value.takeIf { it.autocorrect } }
        focus { it.password }
        type(listOf("t", "e", "h", KeyboardTags.SPACE).map(::keyTag))
        waitFor { scenarioText { it.password }.takeIf { t -> t == "teh " } }
    }

    @Test
    fun undoNeverDeletesWhereTheUserMovedTheCursor() {
        keyboardSettings.settings.value = KeyboardSettings(autocorrect = true)
        waitFor { container.keyboardSettings.value.takeIf { it.autocorrect } }
        focus { it.plain }
        waitFor { onMain { service().spellerLoadedForTest }.takeIf { it } }
        type(listOf("o", "k", KeyboardTags.SPACE, "t", "e", "h", KeyboardTags.SPACE).map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "ok the " } }
        // The user taps right after "ok" (the app moves the cursor), then presses Backspace.
        scenario.onActivity { it.plain.setSelection(2) }
        Thread.sleep(500)
        click(KeyboardTags.KEY_BACKSPACE, inTyping = true)
        // A normal Backspace at the new spot, not an undo of "the".
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "o the " } }
    }

    @Test
    fun aFieldWithTextInItChecksTheCharacterBeforeANewWord() {
        keyboardSettings.settings.value = KeyboardSettings(autocorrect = true)
        waitFor { container.keyboardSettings.value.takeIf { it.autocorrect } }
        // A draft already in the box, like Discord keeps: the word after its space is corrected.
        scenario.onActivity {
            it.plain.setText("hello ")
            it.plain.setSelection(6)
        }
        focus { it.plain }
        waitFor { onMain { service().spellerLoadedForTest }.takeIf { it } }
        type(listOf("w", "l", "r", "l", "d", KeyboardTags.SPACE).map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "hello world " } }
        // Glued to text that was already there: left alone.
        scenario.onActivity {
            it.plain.setText("john@")
            it.plain.setSelection(5)
        }
        Thread.sleep(500)
        type(listOf("g", "m", "i", "a", "l", KeyboardTags.SPACE).map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "john@gmial " } }
    }

    @Test
    fun theStripSuggestsWhileTypingAndATapPutsTheWordIn() {
        focus { it.plain }
        waitFor { onMain { service().spellerLoadedForTest }.takeIf { it } }
        type(listOf("t", "o", "m", "o").map(::keyTag))
        // The best word sits in the middle slot.
        waitFor { onMain { slotText(1) }.takeIf { it == "tomorrow" } }
        click(KeyboardTags.suggestion(1), inTyping = true)
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "tomorrow " } }
        // Between words the strip is gone; the row itself stays.
        waitFor {
            val strip = onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.STRIP) }
            onMain { strip?.isShown == false }.takeIf { it }
        }
    }

    @Test
    fun withAutocorrectTheStripOffersTheFixAndTheTypedWordToKeep() {
        keyboardSettings.settings.value = KeyboardSettings(autocorrect = true)
        waitFor { container.keyboardSettings.value.takeIf { it.autocorrect } }
        focus { it.plain }
        waitFor { onMain { service().spellerLoadedForTest }.takeIf { it } }
        type(listOf("t", "e", "h").map(::keyTag))
        waitFor { onMain { slotText(1) }.takeIf { it == "the" } }
        assertEquals("“teh”", onMain { slotText(0) })
        // Keep what was typed: it goes in as is and is not corrected again in this field.
        click(KeyboardTags.suggestion(0), inTyping = true)
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "teh " } }
        type(listOf("t", "e", "h", KeyboardTags.SPACE).map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "teh teh " } }
    }

    @Test
    fun anAppEndingTheWordNeverGetsItTypedTwice() {
        focus { it.plain }
        type(listOf("h", "e", "y").map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "hey" } }
        // Some apps end the underlined word themselves (on Send, in search boxes) without moving the cursor.
        scenario.onActivity { android.view.inputmethod.BaseInputConnection.removeComposingSpans(it.plain.text) }
        Thread.sleep(500)
        type(listOf("a").map(::keyTag))
        Thread.sleep(500)
        assertEquals("heya", scenarioText { it.plain })
    }

    @Test
    fun typesTextWithShiftSpaceAndBackspace() {
        focus { it.plain }
        type(listOf(KeyboardTags.SHIFT, "h", "i", KeyboardTags.SPACE, "t", "h", "e", "r", "e", "x").map(::keyTag))
        click(KeyboardTags.KEY_BACKSPACE, inTyping = true)
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "Hi there" } }
    }

    @Test
    fun holdingALetterAndSlidingTypesAnAccent() {
        focus { it.plain }
        if (onMain { service().viewForTest } == KeyboardView.STICKERS) click(KeyboardTags.LETTERS)
        val key = waitFor { onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.key("e")) } }
        val down = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float) = touch(key, down, action, x)
        touch(android.view.MotionEvent.ACTION_DOWN, key.width / 2f)
        Thread.sleep(700)
        // The first choice sits above the finger: one key width to the right is the second, "è".
        touch(android.view.MotionEvent.ACTION_MOVE, key.width * 1.5f)
        touch(android.view.MotionEvent.ACTION_UP, key.width * 1.5f)
        waitFor { scenarioText { it.plain }.takeIf { t -> t.isNotEmpty() } }
        assertTrue(scenarioText { it.plain } in setOf("è", "È"), scenarioText { it.plain })
    }

    @Test
    fun slidingOnSpaceMovesTheCursorWithoutTypingASpace() {
        focus { it.plain }
        type(listOf("a", "b", "c").map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "abc" } }
        val space = waitFor { onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.SPACE) } }
        val slop = android.view.ViewConfiguration.get(app).scaledTouchSlop
        val step = app.resources.displayMetrics.density * 12
        val start = onMain { space.width / 2f }
        val down = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float) = touch(space, down, action, x)
        touch(android.view.MotionEvent.ACTION_DOWN, start)
        // Past the slop, then two and a half steps left: the cursor moves two characters.
        touch(android.view.MotionEvent.ACTION_MOVE, start - slop - step * 2.5f)
        touch(android.view.MotionEvent.ACTION_UP, start - slop - step * 2.5f)
        type(listOf("x").map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "axbc" } }
    }

    @Test
    fun holdingSpaceOpensTheKeyboardListAndTypesNothing() {
        focus { it.plain }
        if (onMain { service().viewForTest } == KeyboardView.STICKERS) click(KeyboardTags.LETTERS)
        var opened = 0
        onMain { service().openPicker = { opened++ } }
        val space = waitFor { onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.SPACE) } }
        val x = onMain { space.width / 2f }
        val down = android.os.SystemClock.uptimeMillis()
        touch(space, down, android.view.MotionEvent.ACTION_DOWN, x)
        waitFor { onMain { opened.takeIf { it == 1 } } }
        touch(space, down, android.view.MotionEvent.ACTION_UP, x)
        type(listOf("a").map(::keyTag))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "a" } }
        assertEquals(1, opened)
    }

    @Test
    fun numberRowAndHeightRebuildTheKeyboard() {
        focus { it.plain }
        if (onMain { service().viewForTest } == KeyboardView.STICKERS) click(KeyboardTags.LETTERS)
        val before = onMain { service().typingForTest!!.height }
        assertNull(onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.key("1")) })

        keyboardSettings.settings.value = KeyboardSettings(numberRow = true, height = KeyboardHeight.LARGE)
        waitFor { container.keyboardSettings.value.takeIf { it.numberRow } }
        onMain { service().rebuildForTest() }
        // Letters open with digits on top, and the keyboard is taller.
        click(KeyboardTags.key("1"), inTyping = true)
        waitFor { scenarioText { it.plain }.takeIf { t -> t == "1" } }
        val after = waitFor { onMain { service().typingForTest!!.height.takeIf { h -> h > before } } }
        assertTrue(after > before)
    }

    @Test
    fun toolbarOpensStickersAndEmojiAndKeepsKeySize() {
        focus { it.plain }
        if (onMain { service().viewForTest } == KeyboardView.STICKERS) click(KeyboardTags.LETTERS)
        fun keyHeight() = onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.key("q"))?.height }

        // A new keyboard lays out once before the nav bar inset arrives: wait until the size holds still.
        fun settledKeyHeight(): Int {
            var last = -1
            repeat(20) {
                Thread.sleep(300)
                val now = keyHeight() ?: 0
                if (now > 0 && now == last) return now
                last = now
            }
            fail("key height never settled (last $last)")
        }
        val keyBefore = settledKeyHeight()
        assertNull(onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.TOOLBAR) })

        keyboardSettings.settings.value = KeyboardSettings(toolbar = true)
        waitFor { container.keyboardSettings.value.takeIf { it.toolbar } }
        onMain { service().rebuildForTest() }
        waitFor { onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.TOOLBAR_SWITCH) } }
        // The sticker key moved up to the toolbar, and the letters kept their height.
        assertNull(onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.STICKERS) })
        val keyAfter = settledKeyHeight()
        assertTrue(kotlin.math.abs(keyAfter - keyBefore) <= 2, "$keyBefore -> $keyAfter")

        var opened = 0
        onMain { service().openPicker = { opened++ } }
        click(KeyboardTags.TOOLBAR_SWITCH, inTyping = true)
        waitFor { onMain { opened.takeIf { it == 1 } } }

        click(KeyboardTags.TOOLBAR_EMOJI, inTyping = true)
        waitFor { onMain { service().viewForTest.takeIf { it == KeyboardView.STICKERS } } }
        val grid = waitFor { onMain { service().panelForTest?.findViewWithTag<View>(KeyboardTags.GRID_EMOJI) } }
        waitFor { onMain { grid.isShown.takeIf { it } } }

        click(KeyboardTags.LETTERS)
        click(KeyboardTags.TOOLBAR_STICKERS, inTyping = true)
        waitFor { onMain { service().viewForTest.takeIf { it == KeyboardView.STICKERS } } }
    }

    @Test
    fun accentsAreActionsForTalkBack() {
        focus { it.plain }
        if (onMain { service().viewForTest } == KeyboardView.STICKERS) click(KeyboardTags.LETTERS)
        val key = waitFor { onMain { service().typingForTest?.findViewWithTag<View>(KeyboardTags.key("n")) } }
        val label = app.getString(R.string.kb_type_alternate, "ñ")
        onMain {
            val info = android.view.accessibility.AccessibilityNodeInfo.obtain()
            key.onInitializeAccessibilityNodeInfo(info)
            val action = info.actionList.first { it.label == label }
            key.performAccessibilityAction(action.id, null)
        }
        waitFor { scenarioText { it.plain }.takeIf { t -> t.equals("ñ", ignoreCase = true) } }
    }

    @Test
    fun enterRunsTheFieldsSendAction() {
        focus { it.send }
        type(listOf("o", "k").map(::keyTag))
        click(KeyboardTags.ENTER, inTyping = true)
        var actions = emptyList<Int>()
        waitFor { scenario.onActivity { actions = it.actions.toList() }; actions.takeIf { a -> a.isNotEmpty() } }
        assertEquals(listOf(android.view.inputmethod.EditorInfo.IME_ACTION_SEND), actions)
        assertEquals("ok", scenarioText { it.send })
    }

    @Test
    fun stickerKeyAndAbcSwapViewsAndTypingContinues() {
        focus { it.anyImage }
        if (onMain { service().viewForTest } == KeyboardView.STICKERS) click(KeyboardTags.LETTERS)
        type(listOf("a").map(::keyTag))
        click(KeyboardTags.STICKERS, inTyping = true)
        assertEquals(KeyboardView.STICKERS, onMain { service().viewForTest })
        tapSticker(reactions)
        waitFor { ImeTestActivity.received.firstOrNull() }
        click(KeyboardTags.LETTERS)
        assertEquals(KeyboardView.LETTERS, onMain { service().viewForTest })
        type(listOf("b").map(::keyTag))
        waitFor { scenarioText { it.anyImage }.takeIf { t -> t == "ab" } }
    }

    @Test
    fun emojiTabTypesEmojiByCategory() {
        focusStickers { it.plain }
        tab(KeyboardTags.EMOJI)
        click(KeyboardTags.emojiCategory(EmojiCategory.FOOD.name))
        val first = onMain { EmojiAdapter.shown(EmojiCategory.FOOD).first() }
        click(KeyboardTags.emoji(first))
        waitFor { scenarioText { it.plain }.takeIf { t -> t == first } }
        // The sticker type switch means nothing here, so it is hidden.
        assertEquals(
            View.INVISIBLE,
            onMain { (panel().findViewWithTag<View>(KeyboardTags.kind("ALL")).parent as View).visibility },
        )
    }

    @Test
    fun switchingTabsAndChipsShowsTheRightStickers() {
        focusStickers { it.anyImage }
        tab(KeyboardTags.collection(reactionsId))
        waitFor { gridIds().takeIf { it == setOf(reactions.id, cat.id) } }

        click(KeyboardTags.chip(catsId))
        waitFor { gridIds().takeIf { it == setOf(cat.id) } }

        tab(KeyboardTags.collection(workId))
        waitFor { gridIds().takeIf { it == setOf(webp.id, gif.id) } }

        click(KeyboardTags.kind("ANIMATED"))
        waitFor { gridIds().takeIf { it == setOf(gif.id) } }
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun add(bytes: ByteArray, collectionId: Long): Sticker =
        (container.importer.import(ByteSource { bytes }, collectionId) as ImportResult.Added).sticker

    private fun shell(cmd: String) = Companion.shell(cmd)

    /** Focuses a field, shows the keyboard and points it at the test library. */
    private fun focus(attempt: Int = 1, field: (ImeTestActivity) -> EditText) {
        // showSoftInput is ignored until the window has focus, so keep asking until the panel shows.
        // If another keyboard opened instead, select ours again and restart the field.
        var tries = 0
        waitFor(timeoutMs = 20_000) {
            if (++tries % 10 == 0) shell("ime set $IME_ID")
            scenario.onActivity { activity ->
                val view = field(activity)
                if (activity.hasWindowFocus()) {
                    if (tries % 10 == 0) view.clearFocus()
                    view.requestFocus()
                    activity.getSystemService(InputMethodManager::class.java).showSoftInput(view, 0)
                }
            }
            Thread.sleep(200)
            onMain { StickerImeService.active?.get()?.panelForTest?.takeIf { (it.parent as View).isShown } }
        }
        onMain { service().rebuildForTest() }
        val ready = runCatching {
            waitFor(timeoutMs = 5_000) {
                onMain { panel().takeIf { it.isLaidOut && service().loadedForTest && (it.parent as View).isShown } }
            }
        }
        // The keyboard can hide again while the field settles; show it once more.
        if (ready.isFailure) {
            check(attempt < 3) { "keyboard did not settle" }
            focus(attempt + 1, field)
        }
    }

    /** Opens the field with the sticker panel showing (the keyboard may open on letters). */
    private fun focusStickers(field: (ImeTestActivity) -> EditText) {
        focus(field = field)
        if (onMain { service().viewForTest } != KeyboardView.STICKERS) click(KeyboardTags.STICKERS, inTyping = true)
        waitFor { onMain { panel().takeIf { it.isShown } } }
    }

    private fun service() = StickerImeService.active?.get() ?: fail("keyboard not running")

    private fun panel() = service().panelForTest ?: fail("no panel")

    private fun tab(tag: String) = click(tag)

    private fun click(tag: String, inTyping: Boolean = false) {
        val view = waitFor {
            onMain {
                val root: View = if (inTyping) service().typingForTest ?: fail("no letters") else panel()
                root.findViewWithTag<View>(tag)?.takeIf { it.isShown }
            }
        }
        onMain { view.performClick() }
    }

    /** Taps keys on the letters, switching from the sticker panel first if it is showing. */
    private fun type(keys: List<String>) {
        if (onMain { service().viewForTest } == KeyboardView.STICKERS) click(KeyboardTags.LETTERS)
        keys.forEach { click(it, inTyping = true) }
    }

    /** Letters map to their key tag; other tags pass through. */
    /** The text shown in suggestion slot [slot] (0 to 2), or null. Main thread. */
    private fun slotText(slot: Int): String? {
        val cell = service().typingForTest?.findViewWithTag<android.view.ViewGroup>(KeyboardTags.suggestion(slot))
        return (cell?.getChildAt(0) as? android.widget.TextView)?.text?.toString()
    }

    private fun keyTag(k: String) = if (k.length == 1) KeyboardTags.key(k) else k

    private fun scenarioText(field: (ImeTestActivity) -> EditText): String {
        var text = ""
        scenario.onActivity { text = field(it).text.toString() }
        return text
    }

    private fun tapSticker(sticker: Sticker) = click(KeyboardTags.sticker(sticker.id))

    private fun gridIds(): Set<Long> = onMain {
        val grid = panel().findViewWithTag<RecyclerView>(KeyboardTags.GRID)
        (grid.adapter as StickerAdapter).currentList.map { it.id }.toSet()
    }

    private fun containsText(view: View, text: String): Boolean = when (view) {
        is android.widget.TextView -> view.text.toString() == text
        is android.view.ViewGroup -> (0 until view.childCount).any { containsText(view.getChildAt(it), text) }
        else -> false
    }

    /** Sends one finger event at [x], mid-height, to [view]; [down] is when the press began. */
    private fun touch(view: View, down: Long, action: Int, x: Float) = onMain {
        val now = android.os.SystemClock.uptimeMillis()
        val e = android.view.MotionEvent.obtain(down, now, action, x, view.height / 2f, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun <T : Any> waitFor(timeoutMs: Long = 10_000, check: () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < end) {
            try {
                check()?.let { return it }
            } catch (e: Throwable) {
                last = e
            }
            Thread.sleep(100)
        }
        throw AssertionError("timed out", last)
    }

    companion object {
        private const val IME_ID = "app.kisscut.keys/.keyboard.StickerImeService"
        private var previousIme = ""

        private fun shell(cmd: String): String {
            val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }

        /** Select the sticker keyboard once for the class; switching per test races with the old keyboard. */
        @JvmStatic
        @org.junit.BeforeClass
        fun selectKeyboard() {
            previousIme = shell("settings get secure default_input_method").trim()
            shell("ime enable $IME_ID")
            shell("ime set $IME_ID")
        }

        @JvmStatic
        @org.junit.AfterClass
        fun restoreKeyboard() {
            if (previousIme.isNotEmpty() && previousIme != "null" && previousIme != IME_ID) shell("ime set $previousIme")
        }
    }
}

/** Keyboard settings in memory: tests never write the phone's real settings file. */
private class MemoryKeyboardSettings : KeyboardSettingsStore {
    override val settings = kotlinx.coroutines.flow.MutableStateFlow(KeyboardSettings())
    override suspend fun set(settings: KeyboardSettings) {
        this.settings.value = settings
    }
}
