package com.example.stickers.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageSnifferTest {
    private fun sniff(bytes: ByteArray) = assertNotNull(ImageSniffer.sniff(bytes), "expected a supported image")

    // --- PNG -----------------------------------------------------------------------------------

    @Test
    fun pngWithAlphaChannel() {
        val info = sniff(TestImages.png(width = 640, height = 480, colorType = 6))
        assertEquals(ImageInfo(ImageType.PNG, 640, 480, isAnimated = false, mayHaveAlpha = true), info)
        assertEquals("image/png", info.type.mimeType)
        assertEquals("png", info.type.extension)
    }

    @Test
    fun opaqueRgbPngHasNoAlpha() {
        assertFalse(sniff(TestImages.png(colorType = 2)).mayHaveAlpha)
    }

    @Test
    fun grayAlphaPngMayHaveAlpha() {
        assertTrue(sniff(TestImages.png(colorType = 4)).mayHaveAlpha)
    }

    @Test
    fun trnsChunkMeansMayHaveAlpha() {
        assertTrue(sniff(TestImages.png(colorType = 3, extraChunks = listOf("tRNS"))).mayHaveAlpha)
    }

    @Test
    fun realEncodedPng() {
        val info = sniff(TestImages.realPng(width = 3, height = 2))
        assertEquals(ImageType.PNG, info.type)
        assertEquals(3 to 2, info.width to info.height)
    }

    // --- GIF -----------------------------------------------------------------------------------

    @Test
    fun singleFrameGifIsStatic() {
        val info = sniff(TestImages.gif(width = 4, height = 3, frames = 1))
        assertEquals(ImageInfo(ImageType.GIF, 4, 3, isAnimated = false, mayHaveAlpha = false), info)
        assertEquals("image/gif", info.type.mimeType)
        assertEquals("gif", info.type.extension)
    }

    @Test
    fun multiFrameGifIsAnimated() {
        assertTrue(sniff(TestImages.gif(frames = 3)).isAnimated)
    }

    @Test
    fun commentBytesAreNotCountedAsFrames() {
        assertFalse(sniff(TestImages.gif(frames = 1, commentWith2C = true)).isAnimated)
    }

    @Test
    fun gifTransparencyFlagMeansMayHaveAlpha() {
        assertTrue(sniff(TestImages.gif(transparent = true)).mayHaveAlpha)
    }

    @Test
    fun gifWithoutTrailerStillReadsFrames() {
        assertTrue(sniff(TestImages.gif(frames = 2, trailer = false)).isAnimated)
    }

    @Test
    fun realEncodedGif() {
        val info = sniff(TestImages.realGif(width = 5, height = 7))
        assertEquals(ImageType.GIF, info.type)
        assertEquals(5 to 7, info.width to info.height)
        assertFalse(info.isAnimated)
    }

    // --- WebP ----------------------------------------------------------------------------------

    @Test
    fun lossyWebpIsOpaqueAndStatic() {
        val info = sniff(TestImages.webpLossy(width = 6, height = 5))
        assertEquals(ImageInfo(ImageType.WEBP, 6, 5, isAnimated = false, mayHaveAlpha = false), info)
        assertEquals("image/webp", info.type.mimeType)
        assertEquals("webp", info.type.extension)
    }

    @Test
    fun losslessWebpReadsAlphaBitAndSize() {
        assertEquals(
            ImageInfo(ImageType.WEBP, 6, 5, isAnimated = false, mayHaveAlpha = true),
            sniff(TestImages.webpLossless(width = 6, height = 5, alpha = true)),
        )
        assertFalse(sniff(TestImages.webpLossless(alpha = false)).mayHaveAlpha)
    }

    @Test
    fun extendedWebpReadsFlagsAndCanvas() {
        assertEquals(
            ImageInfo(ImageType.WEBP, 300, 200, isAnimated = true, mayHaveAlpha = true),
            sniff(TestImages.webpExtended(width = 300, height = 200, alpha = true, animated = true)),
        )
        val still = sniff(TestImages.webpExtended(alpha = false, animated = false))
        assertFalse(still.isAnimated)
        assertFalse(still.mayHaveAlpha)
    }

    // --- rejected ------------------------------------------------------------------------------

    @Test
    fun garbageIsRejected() {
        assertNull(ImageSniffer.sniff("not an image at all".toByteArray()))
        assertNull(ImageSniffer.sniff(ByteArray(0)))
    }

    @Test
    fun otherFormatsAreRejected() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(40)
        assertNull(ImageSniffer.sniff(jpeg))
        val wav = "RIFF".toByteArray() + ByteArray(4) + "WAVEfmt ".toByteArray() + ByteArray(30)
        assertNull(ImageSniffer.sniff(wav))
    }

    @Test
    fun truncatedHeadersAreRejected() {
        assertNull(ImageSniffer.sniff(TestImages.png().copyOf(20)))
        assertNull(ImageSniffer.sniff(TestImages.gif().copyOf(12)))
        assertNull(ImageSniffer.sniff(TestImages.webpLossy().copyOf(24)))
    }

    @Test
    fun gifWithNoFramesIsRejected() {
        assertNull(ImageSniffer.sniff(TestImages.gif(frames = 0)))
    }

    @Test
    fun zeroOrNegativeSizeIsRejected() {
        assertNull(ImageSniffer.sniff(TestImages.png(width = 0, height = 10)))
        assertNull(ImageSniffer.sniff(TestImages.png(width = -1, height = 10)))
        assertNull(ImageSniffer.sniff(TestImages.gif(width = 0, height = 3)))
    }
}
