package com.example.stickers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stickers.shared.core.AndroidStillConverter
import com.example.stickers.shared.core.ImageSniffer
import com.example.stickers.shared.core.ImageType
import com.example.stickers.shared.core.ImportLimits
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real decode, scale and lossless encode for the stored format (docs/IMPORT_EXPORT.md), which the JVM can't do. */
@RunWith(AndroidJUnit4::class)
class AndroidStillConverterTest {
    private val converter = AndroidStillConverter()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun convert(bytes: ByteArray) = converter.convert(bytes, assertNotNull(ImageSniffer.sniff(bytes)))

    private val expectedType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ImageType.WEBP else ImageType.PNG

    @Test
    fun largePngIsStoredAt512WithTheSamePixelsAsA512Scale() = runTest {
        val source = TestImageFiles.gradientPng(width = 3000, height = 1500)

        val c = assertNotNull(convert(source))

        assertEquals(expectedType, c.type)
        assertEquals(expectedType, ImageSniffer.sniff(c.bytes)?.type, "bytes really are that type")
        assertTrue(c.scaled)
        assertFalse(c.rotated)
        assertEquals(ImportLimits.STORED_MAX_SIDE, c.width)
        assertEquals(ImportLimits.STORED_MAX_SIDE / 2, c.height)
        // Reference: the same scale of the decoded source, through a lossless PNG round trip (both sides go
        // through the same premultiplied-alpha decode, so any difference is a lossy encode or a wrong scale).
        val reference = decode(png(converter.scaleToStore(decode(source))))
        assertContentEquals(pixels(reference), pixels(decode(c.bytes)), "lossless: identical pixels")
        assertEquals(Color.TRANSPARENT, decode(c.bytes).getPixel(2, 2), "transparency kept")
    }

    @Test
    fun smallImageIsNeverScaledUp() = runTest {
        val c = assertNotNull(convert(TestImageFiles.gradientPng(width = 100, height = 80)))

        assertFalse(c.scaled)
        assertEquals(100, c.width)
        assertEquals(80, c.height)
    }

    @Test
    fun jpegIsTurnedUprightFromItsExifOrientation() = runTest {
        val jpeg = TestImageFiles.halvesJpeg(context, width = 40, height = 20, orientation = 6)

        val c = assertNotNull(convert(jpeg))
        val bmp = decode(c.bytes)

        assertTrue(c.rotated)
        assertEquals(20, bmp.width)
        assertEquals(40, bmp.height)
        // Turned 90 degrees clockwise: the left half (red) is now on top.
        val top = bmp.getPixel(10, 5)
        val bottom = bmp.getPixel(10, 35)
        assertTrue(Color.red(top) > 200 && Color.blue(top) < 60, "top ${Integer.toHexString(top)}")
        assertTrue(Color.blue(bottom) > 200 && Color.red(bottom) < 60, "bottom ${Integer.toHexString(bottom)}")
    }

    @Test
    fun jpegWithoutRotationIsNotMarkedRotated() = runTest {
        val c = assertNotNull(convert(TestImageFiles.halvesJpeg(context, width = 40, height = 20, orientation = 1)))

        assertFalse(c.rotated)
        assertEquals(40, c.width)
    }

    @Test
    fun undecodableBytesGiveNull() = runTest {
        val header = TestImageFiles.png().copyOf(33) // signature + IHDR only
        assertNull(convert(header + ByteArray(200) { 0x5A }))
    }

    private fun decode(bytes: ByteArray): Bitmap = assertNotNull(
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }),
    )

    private fun png(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private fun pixels(b: Bitmap) =
        IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
}
