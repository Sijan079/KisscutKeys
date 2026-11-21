package com.example.stickers.shared.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StickerImporterTest {
    private val db = inMemoryStickerDatabase()
    private val files = RecordingFileStore()
    private val thumbnailer = FakeThumbnailer()
    private val stickers = StickerRepository(db, files, TestClock())
    private val collections = CollectionRepository(db, files, TestClock())
    private val importer = StickerImporter(stickers, files, thumbnailer)

    @AfterTest
    fun close() = db.close()

    private fun source(bytes: ByteArray) = ByteSource { bytes }

    // --- happy path ----------------------------------------------------------------------------

    @Test
    fun importsPngWithFilesNamedByHash() = runTest {
        val cats = collections.create("Cats", null)
        val bytes = TestImages.png(colorType = 6)
        val hash = sha256Hex(bytes)

        val result = assertIs<ImportResult.Added>(importer.import(source(bytes), cats.id))

        with(result.sticker) {
            assertEquals("$hash.png", filePath)
            assertEquals("$hash.webp", thumbPath)
            assertEquals("image/png", mimeType)
            assertEquals(hash, this.hash)
            assertEquals(cats.id, collectionId)
            assertEquals(false, isAnimated)
            assertEquals(true, hasAlpha)
        }
        assertTrue(bytes.contentEquals(files.files.getValue(FileArea.STICKERS to "$hash.png")))
        assertTrue(FakeThumbnailer.THUMB.contentEquals(files.files.getValue(FileArea.THUMBS to "$hash.webp")))
        assertEquals(listOf(hash), stickers.observeInCollection(cats.id).first().map { it.hash })
    }

    @Test
    fun hasAlphaComesFromThePixelsNotTheHeader() = runTest {
        thumbnailer.hasAlpha = false // RGBA header, but every pixel opaque (a typical screenshot)

        val result = assertIs<ImportResult.Added>(importer.import(source(TestImages.png(colorType = 6)), null))

        assertEquals(false, result.sticker.hasAlpha)
    }

    @Test
    fun typeAndExtensionComeFromBytes() = runTest {
        val gif = TestImages.gif(frames = 2)

        val result = assertIs<ImportResult.Added>(importer.import(source(gif), null))

        assertEquals("image/gif", result.sticker.mimeType)
        assertTrue(result.sticker.filePath.endsWith(".gif"))
        assertEquals(true, result.sticker.isAnimated)
    }

    @Test
    fun animatedWebpIsMarkedAnimated() = runTest {
        val webp = TestImages.webpExtended(alpha = true, animated = true)

        val result = assertIs<ImportResult.Added>(importer.import(source(webp), null))

        assertEquals("image/webp", result.sticker.mimeType)
        assertEquals(true, result.sticker.isAnimated)
    }

    @Test
    fun asksTheSourceForAtMostTheStickerLimit() = runTest {
        var asked = -1
        importer.import(ByteSource { max -> asked = max; TestImages.png() }, null)
        assertEquals(ImportLimits.MAX_STICKER_BYTES, asked)
    }

    // --- duplicates ----------------------------------------------------------------------------

    @Test
    fun duplicateIsANoOp() = runTest {
        val cats = collections.create("Cats", null)
        val dogs = collections.create("Dogs", null)
        val bytes = TestImages.png()
        val first = assertIs<ImportResult.Added>(importer.import(source(bytes), cats.id))
        val writesBefore = files.writes.size
        val thumbsBefore = thumbnailer.calls

        val second = assertIs<ImportResult.Duplicate>(importer.import(source(bytes), dogs.id))

        assertEquals(first.sticker.id, second.sticker.id)
        assertEquals(cats.id, second.sticker.collectionId, "duplicate is not moved")
        assertEquals(writesBefore, files.writes.size, "no files written")
        assertEquals(thumbsBefore, thumbnailer.calls, "no thumbnail made")
        assertEquals(1, stickers.allOnce().size)
    }

    // --- rejected ------------------------------------------------------------------------------

    @Test
    fun overSizedStickerIsRejectedWithoutWriting() = runTest {
        val big = TestImages.png() + ByteArray(ImportLimits.MAX_STICKER_BYTES)

        val result = importer.import(source(big), null)

        assertEquals(ImportResult.Rejected(RejectReason.TOO_LARGE), result)
        assertNothingStored()
    }

    @Test
    fun stickerExactlyAtTheLimitIsAccepted() = runTest {
        val png = TestImages.png()
        val atLimit = png + ByteArray(ImportLimits.MAX_STICKER_BYTES - png.size)

        assertIs<ImportResult.Added>(importer.import(source(atLimit), null))
    }

    @Test
    fun hugeDimensionsAreRejected() = runTest {
        val bomb = TestImages.png(width = ImportLimits.MAX_DIMENSION + 1, height = 10)

        assertEquals(ImportResult.Rejected(RejectReason.TOO_LARGE), importer.import(source(bomb), null))
        assertEquals(0, thumbnailer.calls, "never decoded")
    }

    @Test
    fun garbageBytesAreUnsupported() = runTest {
        val result = importer.import(source("hello, this is text".encodeToByteArray()), null)

        assertEquals(ImportResult.Rejected(RejectReason.UNSUPPORTED_TYPE), result)
        assertNothingStored()
    }

    @Test
    fun undecodableImageIsUnreadable() = runTest {
        thumbnailer.fail = true

        val result = importer.import(source(TestImages.png()), null)

        assertEquals(ImportResult.Rejected(RejectReason.UNREADABLE), result)
        assertNothingStored()
    }

    @Test
    fun sourceErrorIsUnreadable() = runTest {
        val result = importer.import(ByteSource { throw IOException("gone") }, null)

        assertEquals(ImportResult.Rejected(RejectReason.UNREADABLE), result)
        assertNothingStored()
    }

    // --- rollback ------------------------------------------------------------------------------

    @Test
    fun failedThumbWriteRemovesTheOriginal() = runTest {
        files.failWritesIn = FileArea.THUMBS

        val result = importer.import(source(TestImages.png()), null)

        assertEquals(ImportResult.Rejected(RejectReason.FAILED), result)
        assertNothingStored()
    }

    @Test
    fun failedInsertRemovesBothFiles() = runTest {
        val result = importer.import(source(TestImages.png()), collectionId = 404) // no such collection

        assertEquals(ImportResult.Rejected(RejectReason.FAILED), result)
        assertNothingStored()
    }

    @Test
    fun cancelledImportStillRemovesItsFiles() = runTest {
        val store = SuspendingFileStore()
        val cancellable = StickerImporter(stickers, store, thumbnailer)

        val job = launch { cancellable.import(source(TestImages.png()), null) }
        store.thumbWriteStarted.await()
        assertEquals(1, store.files.size, "original written, thumb write hanging")
        job.cancelAndJoin()

        assertEquals(emptyMap(), store.files.toMap())
        assertEquals(emptyList(), stickers.allOnce())
    }

    private suspend fun assertNothingStored() {
        assertEquals(emptyMap(), files.files.toMap())
        assertEquals(emptyList(), stickers.allOnce())
    }
}

/** [FakeFileStore] that also records writes and can fail writes in one area. */
class RecordingFileStore : FileStore {
    private val inner = FakeFileStore()
    val files get() = inner.files
    val writes = mutableListOf<Pair<FileArea, String>>()
    var failWritesIn: FileArea? = null

    override suspend fun write(area: FileArea, relativePath: String, bytes: ByteArray) {
        if (area == failWritesIn) throw IOException("disk full")
        writes += area to relativePath
        inner.write(area, relativePath, bytes)
    }

    override suspend fun exists(area: FileArea, relativePath: String) = inner.exists(area, relativePath)

    override suspend fun delete(area: FileArea, relativePath: String) = inner.delete(area, relativePath)
}

/** Thumb writes hang; deletes check cancellation first, like AndroidFileStore's withContext. */
class SuspendingFileStore : FileStore {
    private val inner = FakeFileStore()
    val files get() = inner.files
    val thumbWriteStarted = CompletableDeferred<Unit>()

    override suspend fun write(area: FileArea, relativePath: String, bytes: ByteArray) {
        if (area == FileArea.THUMBS) {
            thumbWriteStarted.complete(Unit)
            awaitCancellation()
        }
        inner.write(area, relativePath, bytes)
    }

    override suspend fun exists(area: FileArea, relativePath: String) = inner.exists(area, relativePath)

    override suspend fun delete(area: FileArea, relativePath: String): Boolean {
        currentCoroutineContext().ensureActive()
        return inner.delete(area, relativePath)
    }
}

class FakeThumbnailer : Thumbnailer {
    var hasAlpha = true
    var fail = false
    var calls = 0

    override suspend fun thumbnail(bytes: ByteArray, info: ImageInfo): Thumbnail? {
        calls++
        return if (fail) null else Thumbnail(THUMB, hasAlpha)
    }

    companion object {
        val THUMB = byteArrayOf(0x57, 0x45, 0x42, 0x50)
    }
}
