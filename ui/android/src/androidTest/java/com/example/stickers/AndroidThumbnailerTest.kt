package com.example.stickers

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stickers.shared.core.AndroidThumbnailer
import com.example.stickers.shared.core.ImageSniffer
import com.example.stickers.shared.core.ImportLimits
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
        val t = assertNotNull(thumb(TestImageFiles.png(width = 1600, height = 800)))

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(t.webpBytes, 0, t.webpBytes.size, bounds)
        assertEquals(ImportLimits.THUMB_MAX_SIDE, bounds.outWidth)
        assertEquals(ImportLimits.THUMB_MAX_SIDE / 2, bounds.outHeight)
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
