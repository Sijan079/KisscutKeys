package com.example.stickers

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stickers.save.SaveTags
import com.example.stickers.shared.core.FileArea
import com.example.stickers.shared.core.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End to end on the real app: a share intent opens the Save sheet, Save stores the sticker in the
 * one database, and sharing the same image again changes nothing.
 */
@RunWith(AndroidJUnit4::class)
class ShareIntentTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<App>()
    private val container = app.container
    private val mediaUris = mutableListOf<Uri>()
    private val hashes = mutableListOf<String>()

    @After
    fun cleanUp() = runBlocking {
        val ids = hashes.mapNotNull { container.stickers.findByHash(it)?.id }
        container.stickers.delete(ids)
        mediaUris.forEach { app.contentResolver.delete(it, null, null) }
    }

    private fun newImage(): Uri {
        val bytes = TestImageFiles.png(seed = (System.nanoTime() % 10_000).toInt(), width = 48 + hashes.size)
        hashes += sha256Hex(bytes)
        return TestImageFiles.insertImage(app, bytes, "share-test-${System.nanoTime()}.png").also { mediaUris += it }
    }

    private fun shareAndSave(intent: Intent, expectedSummary: String) {
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTagExists(SaveTags.SAVE_BUTTON)
            }
            compose.onNodeWithTag(SaveTags.SAVE_BUTTON).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTagExists(SaveTags.SUMMARY) }
            compose.onNodeWithText(expectedSummary).assertExists()
            compose.onNodeWithText("Done").performClick()
        }
    }

    private fun send(uri: Uri) = Intent(app, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Test
    fun sharedImageIsSavedAndADuplicateShareIsANoOp() = runBlocking {
        val uri = newImage()

        shareAndSave(send(uri), "1 sticker saved")

        val saved = assertNotNull(container.stickers.findByHash(hashes.single()))
        // Stored as PNG or lossless WebP, whichever is smaller.
        assertTrue(saved.mimeType in setOf("image/png", "image/webp"), saved.mimeType)
        assertTrue(container.fileStore.exists(FileArea.STICKERS, saved.filePath))
        assertTrue(container.fileStore.exists(FileArea.THUMBS, saved.thumbPath))
        val countBefore = container.stickers.allOnce().size

        shareAndSave(send(uri), "1 was already in your library")

        assertEquals(countBefore, container.stickers.allOnce().size, "no second row")
        assertEquals(saved, container.stickers.findByHash(hashes.single()), "existing sticker unchanged")
    }

    @Test
    fun sendMultipleSavesEveryImage() = runBlocking {
        val uris = arrayListOf(newImage(), newImage())
        val intent = Intent(app, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND_MULTIPLE)
            .setType("image/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)

        shareAndSave(intent, "2 stickers saved")

        hashes.forEach { assertNotNull(container.stickers.findByHash(it)) }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(tag: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
