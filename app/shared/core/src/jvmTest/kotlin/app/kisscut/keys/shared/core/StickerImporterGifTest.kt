package app.kisscut.keys.shared.core

import app.kisscut.keys.shared.core.gif.GifCanvas
import app.kisscut.keys.shared.core.gif.GifReader
import app.kisscut.keys.shared.core.gif.TestGif
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Large GIFs at import (docs/IMPORT_EXPORT.md, Stored format). */
class StickerImporterGifTest {
    private val db = inMemoryStickerDatabase()
    private val files = RecordingFileStore()
    private val thumbnailer = FakeThumbnailer()
    private val stickers = StickerRepository(db, files, TestClock())
    private val importer = StickerImporter(stickers, files, thumbnailer, FakeStillConverter())

    @AfterTest
    fun close() = db.close()

    private fun source(bytes: ByteArray) = ByteSource { bytes }

    /** [frames] frames of a [width] x [height] sky with a square moving right; 4 colors, compresses well. */
    private fun movingSquare(width: Int, height: Int, frames: Int, delayCs: Int = 6): ByteArray =
        TestGif(width, height, intArrayOf(0x3080FF, 0xFF4020, 0x20C040, 0), "NETSCAPE2.0", 0).apply {
            repeat(frames) { f ->
                val side = height / 4
                val x0 = f * (width - side) / maxOf(1, frames - 1)
                val indices = ByteArray(width * height) { p ->
                    val x = p % width
                    val y = p / width
                    if (x in x0 until x0 + side && y in side until 2 * side) 1 else if (y > height - 20) 2 else 0
                }
                frame(TestGif.Frame(indices, width, height, control = TestGif.Control(1, delayCs + f)))
            }
        }.bytes()

    private fun storedBytes(sticker: app.kisscut.keys.shared.db.Sticker) =
        files.files.getValue(FileArea.STICKERS to sticker.filePath)

    @Test
    fun largeGifIsStoredAsASmallerGifWithTheSameTiming() = runTest {
        val gif = movingSquare(1000, 600, frames = 6)
        val result = assertIs<ImportResult.Added>(importer.import(source(gif), null))

        val sticker = result.sticker
        assertEquals("image/gif", sticker.mimeType)
        assertTrue(sticker.isAnimated)
        assertEquals(sha256Hex(gif), sticker.hash, "hashed as received")
        assertEquals("${sha256Hex(gif)}.gif", sticker.filePath)
        val stored = storedBytes(sticker)
        assertTrue(stored.size < gif.size)
        assertContentEquals(stored, thumbnailer.lastBytes, "thumbnail made from the stored file")

        val reader = GifReader(stored)
        assertEquals(512 to 307, reader.width to reader.height)
        assertEquals(0, reader.loopCount)
        assertEquals((6 until 12).toList(), reader.frames().asSequence().map { it.delayCs }.toList())
    }

    @Test
    fun shrunkGifLooksLikeTheScaledSource() = runTest {
        val gif = movingSquare(800, 800, frames = 3)
        val stored = storedBytes(assertIs<ImportResult.Added>(importer.import(source(gif), null)).sticker)
        val source = GifReader(gif)
        val shrunk = GifReader(stored)
        val a = GifCanvas(source.width, source.height)
        val b = GifCanvas(shrunk.width, shrunk.height)
        source.frames().asSequence().zip(shrunk.frames().asSequence()).forEach { (fa, fb) ->
            val big = a.draw(fa)
            val small = b.draw(fb)
            // Sample the middle of output pixels well inside each colored area: colors must be exact.
            for ((x, y) in listOf(10 to 10, 256 to 20, 300 to 505)) {
                assertEquals(big[(y * 800 / 512) * 800 + x * 800 / 512], small[y * 512 + x], "pixel $x,$y")
            }
        }
    }

    @Test
    fun smallGifIsStoredUnchanged() = runTest {
        val gif = movingSquare(512, 300, frames = 3)
        val result = assertIs<ImportResult.Added>(importer.import(source(gif), null))
        assertContentEquals(gif, storedBytes(result.sticker))
    }

    @Test
    fun gifIsKeptWhenShrinkingWouldMakeItBigger() = runTest {
        // Two-color noise packs 8 pixels per byte; scaled, it blends into hundreds of colors at 8 bits each.
        val r = Random(1)
        val gif = TestGif(600, 600, intArrayOf(0x000000, 0xFFFFFF))
            .frame(TestGif.Frame(ByteArray(600 * 600) { r.nextInt(2).toByte() }, 600, 600)).bytes()
        val result = assertIs<ImportResult.Added>(importer.import(source(gif), null))
        assertContentEquals(gif, storedBytes(result.sticker))
    }

    @Test
    fun gifThatCannotBeShrunkIsStoredAsReceived() = runTest {
        // Over the GIF canvas limit (2048 x 2048) but under MAX_DIMENSION: not decoded, kept as it is.
        val gif = TestImages.gif(width = 4000, height = 3000, frames = 2)
        val result = assertIs<ImportResult.Added>(importer.import(source(gif), null))
        assertContentEquals(gif, storedBytes(result.sticker))
        assertTrue(result.sticker.isAnimated)
    }

    @Test
    fun reportsProgressPerFrame() = runTest {
        val seen = mutableListOf<Float>()
        importer.import(source(movingSquare(1000, 600, frames = 4)), null) { synchronized(seen) { seen += it } }
        assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f), seen)
    }

    @Test
    fun noProgressForOtherImages() = runTest {
        var calls = 0
        importer.import(source(movingSquare(300, 200, frames = 2)), null) { calls++ }
        importer.import(source(TestImages.png()), null) { calls++ }
        assertEquals(0, calls)
    }

    @Test
    fun cancellingWhileShrinkingStoresNothing() = runTest {
        var job: Job? = null
        job = launch { importer.import(source(movingSquare(1000, 600, frames = 20)), null) { job?.cancel() } }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(emptyMap(), files.files.toMap())
        assertEquals(emptyList(), stickers.allOnce())
    }
}
