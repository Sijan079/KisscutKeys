package com.example.stickers.keyboard

import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stickers.App
import com.example.stickers.AppContainer
import com.example.stickers.R
import com.example.stickers.debug.ImeTestActivity
import com.example.stickers.debug.SeedData
import com.example.stickers.keyboard.StickerImeService.Companion.inserterForTest
import com.example.stickers.keyboard.StickerImeService.Companion.loadedForTest
import com.example.stickers.keyboard.StickerImeService.Companion.panelForTest
import com.example.stickers.keyboard.StickerImeService.Companion.rebuildForTest
import com.example.stickers.shared.core.ByteSource
import com.example.stickers.shared.core.ImportResult
import com.example.stickers.shared.db.Sticker
import com.example.stickers.shared.db.StickerDatabase
import com.example.stickers.shared.db.buildStickerDatabase
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
        container = AppContainer(app, db, dir)
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
        focus { it.anyImage }
        tapSticker(reactions)

        val got = waitFor { ImeTestActivity.received.firstOrNull() }
        assertEquals(ImeTestActivity.FIELD_ANY, got.field)
        assertEquals("image/png", got.mimeType)
        assertEquals("image/png", got.resolvedType)
        waitFor { service().lastOutcome?.takeIf { it == InsertOutcome.SENT } }

        val used = waitFor { runBlocking { container.stickers.observeRecents().first() }.firstOrNull() }
        assertEquals(reactions.id, used.id)
        assertEquals(1, used.useCount)
    }

    @Test
    fun webpGoesAsPngWhereOnlyPngIsAccepted() {
        focus { it.pngOnly }
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
        focus { it.pngOnly }
        tab(KeyboardTags.collection(workId))
        tapSticker(gif)

        waitFor { service().lastOutcome?.takeIf { it == InsertOutcome.COPIED_TYPE_REJECTED } }
        assertEquals(app.getString(R.string.kb_hint_type_rejected), onMain { panel().hintText?.toString() })
        assertTrue(ImeTestActivity.received.isEmpty())
    }

    @Test
    fun fieldWithoutImagesGetsClipboardAndHint() {
        focus { it.plain }
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
    fun passwordFieldDisablesStickers() {
        focus { it.password }
        waitFor {
            onMain {
                panel().findViewWithTag<View>(KeyboardTags.MESSAGE)
                    ?.takeIf { it.visibility == View.VISIBLE && containsText(it, app.getString(R.string.kb_password)) }
            }
        }
        assertTrue(ImeTestActivity.received.isEmpty())
    }

    @Test
    fun switchingTabsAndChipsShowsTheRightStickers() {
        focus { it.anyImage }
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
            onMain { StickerImeService.active?.get()?.panelForTest?.takeIf { it.isShown } }
        }
        onMain { service().rebuildForTest() }
        val ready = runCatching {
            waitFor(timeoutMs = 5_000) {
                onMain { panel().takeIf { it.isShown && it.isLaidOut && service().loadedForTest } }
            }
        }
        // The keyboard can hide again while the field settles; show it once more.
        if (ready.isFailure) {
            check(attempt < 3) { "keyboard did not settle" }
            focus(attempt + 1, field)
        }
    }

    private fun service() = StickerImeService.active?.get() ?: fail("keyboard not running")

    private fun panel() = service().panelForTest ?: fail("no panel")

    private fun tab(tag: String) = click(tag)

    private fun click(tag: String) {
        val view = waitFor { onMain { panel().findViewWithTag<View>(tag)?.takeIf { it.isShown } } }
        onMain { view.performClick() }
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
        private const val IME_ID = "com.example.stickers/.keyboard.StickerImeService"
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
