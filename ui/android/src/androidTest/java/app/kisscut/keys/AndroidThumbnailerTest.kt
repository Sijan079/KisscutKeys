package app.kisscut.keys

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kisscut.keys.shared.core.AndroidThumbnailer
import app.kisscut.keys.shared.core.DieCut
import app.kisscut.keys.shared.core.ImageSniffer
import app.kisscut.keys.shared.core.ImportLimits
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real BitmapFactory decode and WebP encode, which JVM tests cannot reach. */
@RunWith(AndroidJUnit4::class)
class AndroidThumbnailerTest {
    private val thumbnailer = AndroidThumbnailer()

    private suspend fun thumb(bytes: ByteArray) =
        thumbnailer.thumbnail(bytes, assertNotNull(ImageSniffer.sniff(bytes)))

    @Test
    fun transparentPngHasAlphaAndBecomesWebp() = runTest {
        val t = assertNotNull(thumb(TestImageFiles.png(transparent = true)))

        assertTrue(t.hasAlpha)
        assertEquals("RIFF", t.webpBytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WEBP", t.webpBytes.copyOfRange(8, 12).decodeToString())
    }

    @Test
    fun opaquePngWithAlphaChannelHasNoAlpha() = runTest {
        val t = assertNotNull(thumb(TestImageFiles.png(transparent = false)))

        assertFalse(t.hasAlpha, "every pixel opaque, so no cut line")
    }

    @Test
    fun largeImageIsScaledToTheThumbSize() = runTest {
        val t = assertNotNull(thumb(TestImageFiles.png(width = 1600, height = 800, transparent = false)))

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(t.webpBytes, 0, t.webpBytes.size, bounds)
        assertEquals(ImportLimits.THUMB_MAX_SIDE, bounds.outWidth)
        assertEquals(ImportLimits.THUMB_MAX_SIDE / 2, bounds.outHeight)
    }

    @Test
    fun transparentStickerGetsAPaddedWhiteCutLine() = runTest {
        val t = assertNotNull(thumb(TestImageFiles.circlePng(size = 200)))
        val bmp = decode(t.webpBytes)
        val pad = DieCut.padFor(DieCut.radiusFor(ImportLimits.THUMB_MAX_SIDE))

        assertTrue(t.hasAlpha)
        assertEquals(200 + 2 * pad, bmp.width)
        // Circle edge is at 100 - 80 = 20 px from the source's left; the line sits just outside it.
        val y = bmp.height / 2
        val line = bmp.getPixel(pad + 20 - 4, y)
        assertTrue(Color.alpha(line) > 200 && Color.red(line) > 230 && Color.blue(line) > 230, "line ${hex(line)}")
        // Far corner stays transparent; the sticker colour stays inside.
        assertTrue(Color.alpha(bmp.getPixel(1, 1)) < 20)
        assertTrue(Color.blue(bmp.getPixel(bmp.width / 2, y)) < 150, "centre keeps its colour")
    }

    @Test
    fun opaqueImageIsNotPadded() = runTest {
        val t = assertNotNull(thumb(TestImageFiles.png(width = 200, height = 120, transparent = false)))
        val bmp = decode(t.webpBytes)

        assertEquals(200, bmp.width)
        assertEquals(120, bmp.height)
    }

    @Test
    fun largeTransparentStickerStaysWithinTheThumbSize() = runTest {
        val t = assertNotNull(thumb(TestImageFiles.circlePng(size = 1000)))
        val bmp = decode(t.webpBytes)

        assertEquals(ImportLimits.THUMB_MAX_SIDE, bmp.width)
        assertEquals(ImportLimits.THUMB_MAX_SIDE, bmp.height)
    }

    private fun decode(bytes: ByteArray): Bitmap = assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))

    private fun hex(c: Int) = Integer.toHexString(c)

    @Test
    fun transparentWebpIsDecoded() = runTest {
        val t = assertNotNull(thumb(TestImageFiles.webp(transparent = true)))

        assertTrue(t.hasAlpha)
    }

    @Test
    fun gifDecodesItsFirstFrame() = runTest {
        val t = assertNotNull(thumb(TestImageFiles.GIF_1X1))

        assertTrue(t.hasAlpha)
    }

    @Test
    fun validHeaderWithGarbageBodyIsNull() = runTest {
        val header = TestImageFiles.png().copyOf(33) // signature + IHDR only
        val bytes = header + ByteArray(200) { 0x5A }

        assertNull(thumb(bytes))
    }
}
