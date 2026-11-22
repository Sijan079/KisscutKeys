package com.example.stickers

import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stickers.save.SaveTags
import com.example.stickers.shared.core.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Picking the same image again (activity stays open) must open a fresh Save sheet, not the old result. */
@RunWith(AndroidJUnit4::class)
class RepickTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<App>()
    private val bytes = TestImageFiles.png(width = 91, seed = (System.nanoTime() % 10_000).toInt())
    private val uri: Uri = TestImageFiles.insertImage(app, bytes, "repick-${System.nanoTime()}.png")

    @After
    fun cleanUp() {
        runBlocking {
            app.container.stickers.findByHash(sha256Hex(bytes))?.let { app.container.stickers.delete(listOf(it.id)) }
        }
        app.contentResolver.delete(uri, null, null)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun samePickTwiceShowsTheSaveButtonAgain() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.openSave(listOf(uri.toString()), fromShare = false) }
            compose.waitUntil(10_000) { exists(SaveTags.SAVE_BUTTON) }
            compose.onNodeWithTag(SaveTags.SAVE_BUTTON).performClick()
            compose.waitUntil(10_000) { exists(SaveTags.SUMMARY) }
            compose.onNodeWithTag(SaveTags.DONE_BUTTON).performClick()
            compose.waitUntil(5_000) { !exists(SaveTags.SUMMARY) }

            scenario.onActivity { it.openSave(listOf(uri.toString()), fromShare = false) }

            compose.waitUntil(10_000) { exists(SaveTags.SAVE_BUTTON) || exists(SaveTags.SUMMARY) }
            compose.onNodeWithTag(SaveTags.SAVE_BUTTON).assertExists()
        }
    }
}
