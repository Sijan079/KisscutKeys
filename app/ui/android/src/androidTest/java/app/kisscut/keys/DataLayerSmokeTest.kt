package app.kisscut.keys

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kisscut.keys.shared.core.DeleteMode
import app.kisscut.keys.shared.core.FileArea
import app.kisscut.keys.shared.core.NewSticker
import app.kisscut.keys.shared.db.DatabaseInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * On-device check of what JVM tests cannot reach: the real Android database builder, the bundled
 * SQLite native library on Android, and AndroidFileStore under the app's filesDir.
 * Cleans up after itself.
 */
@RunWith(AndroidJUnit4::class)
class DataLayerSmokeTest {
    private val app = ApplicationProvider.getApplicationContext<App>()
    private val container = app.container

    @Test
    fun containerOpensTheOneDatabaseAndRoundTripsData() = runTest {
        val hash = "smoke-${System.nanoTime()}"
        container.fileStore.write(FileArea.STICKERS, "$hash.png", byteArrayOf(1))
        container.fileStore.write(FileArea.THUMBS, "$hash.webp", byteArrayOf(2))

        val collection = container.collections.create("Smoke test", parentId = null)
        val added = container.stickers.add(
            NewSticker("$hash.png", "$hash.webp", "image/png", isAnimated = false, hasAlpha = true, hash = hash),
            collectionId = collection.id,
        )
        assertTrue(added.isNew)
        assertEquals(1, container.collections.totalStickerCount(collection.id))
        assertTrue(app.getDatabasePath(DatabaseInfo.FILE_NAME).exists(), "uses the single named database file")

        val report = container.collections.delete(collection.id, DeleteMode.EVERYTHING)
        assertEquals(emptyList(), report.failed)
        assertFalse(container.fileStore.exists(FileArea.STICKERS, "$hash.png"))
        assertFalse(container.fileStore.exists(FileArea.THUMBS, "$hash.webp"))
    }
}
