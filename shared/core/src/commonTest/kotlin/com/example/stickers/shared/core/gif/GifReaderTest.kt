package com.example.stickers.shared.core.gif

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GifReaderTest {
    private fun palette(size: Int) = IntArray(size) { (it * 0x010203) and 0xFFFFFF }

    private fun noise(size: Int, colors: Int, seed: Int = 1) = Random(seed).let { r ->
        ByteArray(size) { r.nextInt(colors).toByte() }
    }

    private fun single(
        width: Int,
        height: Int,
        indices: ByteArray,
        colors: Int = 256,
        startWithClear: Boolean = true,
        clearWhenFull: Boolean = true,
        interlaced: Boolean = false,
    ) = TestGif(width, height, palette(colors)).frame(
        TestGif.Frame(
            indices, width, height,
            interlaced = interlaced, startWithClear = startWithClear, clearWhenFull = clearWhenFull,
        ),
    ).bytes()

    private fun GifReader.all() = frames().asSequence().toList()

    private fun assertDecodes(expected: ByteArray, gif: ByteArray) {
        val frame = GifReader(gif).all().single()
        assertContentEquals(expected, frame.indices)
        assertNull(frame.missing)
    }

    // --- Header and frames -----------------------------------------------------------------------

    @Test
    fun readsSizeFramesAndPalette() {
        val reader = GifReader(single(3, 2, byteArrayOf(0, 1, 2, 3, 2, 1), colors = 4))
        assertEquals(3, reader.width)
        assertEquals(2, reader.height)
        assertEquals(1, reader.frameCount)
        assertNull(reader.loopCount)
        val frame = reader.all().single()
        assertContentEquals(byteArrayOf(0, 1, 2, 3, 2, 1), frame.indices)
        assertEquals(0xFF000000.toInt() or 0x020406, frame.palette[2])
        assertEquals(-1, frame.transparentIndex)
    }

    @Test
    fun localPaletteOverridesGlobal() {
        val local = intArrayOf(0x112233, 0x445566)
        val gif = TestGif(2, 1, palette(4)).frame(TestGif.Frame(byteArrayOf(1, 0), 2, 1, localPalette = local))
            .frame(TestGif.Frame(byteArrayOf(1, 0), 2, 1)).bytes()
        val (first, second) = GifReader(gif).all()
        assertEquals(0xFF445566.toInt(), first.palette[1])
        assertEquals(4, second.palette.size)
    }

    @Test
    fun loopCountFromNetscapeAndAnimexts() {
        val frame = TestGif.Frame(byteArrayOf(0), 1, 1)
        assertEquals(0, GifReader(TestGif(1, 1, palette(2), "NETSCAPE2.0", 0).frame(frame).bytes()).loopCount)
        assertEquals(3, GifReader(TestGif(1, 1, palette(2), "NETSCAPE2.0", 3).frame(frame).bytes()).loopCount)
        assertEquals(7, GifReader(TestGif(1, 1, palette(2), "ANIMEXTS1.0", 7).frame(frame).bytes()).loopCount)
        assertNull(GifReader(TestGif(1, 1, palette(2), "XMP DataXMP", 7).frame(frame).bytes()).loopCount)
    }

    @Test
    fun controlExtensionAppliesToTheNextImageOnly() {
        val gif = TestGif(1, 1, palette(4))
            .frame(TestGif.Frame(byteArrayOf(0), 1, 1, control = TestGif.Control(3, 25, 2)))
            .frame(TestGif.Frame(byteArrayOf(0), 1, 1, control = null))
            .frame(TestGif.Frame(byteArrayOf(0), 1, 1, control = TestGif.Control(2, 7)))
            .frame(TestGif.Frame(byteArrayOf(0), 1, 1, control = TestGif.Control(5, 1)))
            .bytes()
        val (a, b, c, d) = GifReader(gif).all()
        assertEquals(Triple(GifDisposal.RESTORE_PREVIOUS, 25, 2), Triple(a.disposal, a.delayCs, a.transparentIndex))
        assertEquals(Triple(GifDisposal.KEEP, 0, -1), Triple(b.disposal, b.delayCs, b.transparentIndex))
        assertEquals(Triple(GifDisposal.CLEAR, 7, -1), Triple(c.disposal, c.delayCs, c.transparentIndex))
        assertEquals(GifDisposal.KEEP, d.disposal) // undefined methods 4-7 keep the frame
    }

    // --- LZW -------------------------------------------------------------------------------------

    @Test
    fun noiseGrowsCodeWidthAndClearsWhenTableFills() {
        val pixels = noise(300 * 300, 256)
        assertDecodes(pixels, single(300, 300, pixels))
    }

    @Test
    fun fullTableContinuesWithoutClearCode() {
        val pixels = noise(300 * 300, 256, seed = 2)
        assertDecodes(pixels, single(300, 300, pixels, clearWhenFull = false))
    }

    @Test
    fun oneColorRunUsesTheCodeBeingDefined() {
        // "aaaa..." makes the encoder send each code right after defining it (the KwKwK case).
        val pixels = ByteArray(5000) { 1 }
        assertDecodes(pixels, single(100, 50, pixels, colors = 2))
    }

    @Test
    fun streamWithoutLeadingClearCode() {
        val pixels = noise(64 * 64, 16, seed = 3)
        assertDecodes(pixels, single(64, 64, pixels, colors = 16, startWithClear = false))
    }

    @Test
    fun everyMinimumCodeSize() {
        for (bits in 1..8) {
            val colors = 1 shl bits
            val pixels = noise(40 * 30, colors, seed = bits)
            assertDecodes(pixels, single(40, 30, pixels, colors = colors))
        }
    }

    @Test
    fun interlacedHeightsOneToNine() {
        for (h in 1..9) {
            val pixels = noise(5 * h, 4, seed = h)
            assertDecodes(pixels, single(5, h, pixels, colors = 4, interlaced = true))
        }
    }

    @Test
    fun extraDataAfterAFullFrameIsIgnored() {
        val pixels = byteArrayOf(1, 2, 3, 0)
        val data = TestGif.lzw(pixels + pixels, 2)
        val gif = TestGif(2, 2, palette(4)).frame(TestGif.Frame(pixels, 2, 2, rawData = subBlocks(data))).bytes()
        assertDecodes(pixels, gif)
    }

    // --- Damaged files ---------------------------------------------------------------------------

    @Test
    fun missingTrailerKeepsTheFrames() {
        val gif = TestGif(1, 1, palette(2)).frame(TestGif.Frame(byteArrayOf(1), 1, 1))
            .frame(TestGif.Frame(byteArrayOf(0), 1, 1)).bytes(trailer = false)
        assertEquals(2, GifReader(gif).frameCount)
    }

    @Test
    fun junkAfterAFrameEndsTheGif() {
        val gif = TestGif(1, 1, palette(2)).frame(TestGif.Frame(byteArrayOf(1), 1, 1)).bytes(trailer = false) +
            byteArrayOf(0x55, 0x2C, 0, 0)
        assertEquals(1, GifReader(gif).frameCount)
    }

    @Test
    fun truncatedPixelDataMarksTheRestMissing() {
        val pixels = noise(50 * 50, 256)
        val full = single(50, 50, pixels)
        val cut = full.copyOf(full.size - 600)
        val frame = GifReader(cut).all().single()
        val missing = assertNotNullMissing(frame)
        val decoded = missing.indexOfFirst { it != 0.toByte() }
        assertTrue(decoded in 1 until pixels.size, "some pixels decoded, not all: $decoded")
        assertContentEquals(pixels.copyOf(decoded), frame.indices.copyOf(decoded))
        assertTrue(missing.drop(decoded).all { it != 0.toByte() })
    }

    @Test
    fun truncatedInterlacedFrameMarksTheMissingRows() {
        // 4 x 8 interlaced: rows arrive 0, 4, 2, 6, 1, 3, 5, 7. Only the first two rows of data are present.
        val rows = noise(4 * 8, 4)
        val sent = TestGif.interlace(rows, 4, 8).copyOf(8)
        val gif = TestGif(4, 8, palette(4))
            .frame(TestGif.Frame(rows, 4, 8, interlaced = true, rawData = subBlocks(TestGif.lzw(sent, 2)))).bytes()
        val missing = assertNotNullMissing(GifReader(gif).all().single())
        val missingRows = (0 until 8).filter { y -> missing[y * 4] != 0.toByte() }
        assertEquals(listOf(1, 2, 3, 5, 6, 7), missingRows)
    }

    @Test
    fun undefinedCodeStopsTheFrameWithoutCrashing() {
        // Min code size 2: clear = 4, end = 5. After clear, literal 1, then code 7 (only 6 could be next).
        val bits = TestGif.Companion.BitPacker().apply {
            put(4, 3)
            put(1, 3)
            put(7, 3)
            put(2, 3)
        }.toByteArray()
        val gif = TestGif(2, 2, palette(4))
            .frame(TestGif.Frame(ByteArray(4), 2, 2, rawData = subBlocks(bits)))
            .frame(TestGif.Frame(byteArrayOf(3, 3, 3, 3), 2, 2)).bytes()
        val (broken, next) = GifReader(gif).all()
        assertContentEquals(byteArrayOf(0, 1, 1, 1), assertNotNullMissing(broken))
        assertEquals(1, broken.indices[0].toInt())
        assertContentEquals(byteArrayOf(3, 3, 3, 3), next.indices)
    }

    @Test
    fun codeAfterClearMustBeALiteral() {
        val bits = TestGif.Companion.BitPacker().apply {
            put(4, 3)
            put(6, 3)
        }.toByteArray()
        val gif = TestGif(1, 1, palette(4)).frame(TestGif.Frame(ByteArray(1), 1, 1, rawData = subBlocks(bits))).bytes()
        assertContentEquals(byteArrayOf(1), assertNotNullMissing(GifReader(gif).all().single()))
    }

    @Test
    fun badCodeSizeOrMissingPaletteEndsTheGifThere() {
        val ok = TestGif.Frame(byteArrayOf(0), 1, 1)
        for (size in listOf(0, 1, 9, 12)) {
            val gif = TestGif(1, 1, palette(2)).frame(ok)
                .frame(TestGif.Frame(byteArrayOf(0), 1, 1, minCodeSize = size, rawData = byteArrayOf(0))).bytes()
            assertEquals(1, GifReader(gif).frameCount, "code size $size")
        }
        val noPalette = TestGif(1, 1, null).frame(ok).bytes()
        assertFailsWith<GifException> { GifReader(noPalette) }
    }

    @Test
    fun subBlockPastTheEnd() {
        val gif = TestGif(1, 1, palette(2)).frame(TestGif.Frame(byteArrayOf(1), 1, 1)).bytes(trailer = false) +
            byteArrayOf(0x21, 0xFE.toByte(), 200.toByte(), 1, 2)
        assertEquals(1, GifReader(gif).frameCount)
        val cutFrame = TestGif(1, 1, palette(2)).frame(TestGif.Frame(byteArrayOf(1), 1, 1)).bytes()
        val lastByteOfData = cutFrame.size - 2
        val frame = GifReader(cutFrame.copyOf(lastByteOfData)).all().single() // terminator and trailer gone
        assertEquals(1, frame.indices[0].toInt())
    }

    // --- Limits ----------------------------------------------------------------------------------

    @Test
    fun rejectsNonGifAndEmptyScreen() {
        assertFailsWith<GifException> { GifReader(byteArrayOf()) }
        assertFailsWith<GifException> { GifReader("GIF89a".encodeToByteArray()) }
        assertFailsWith<GifException> { GifReader("PNG000000000000".encodeToByteArray()) }
        val frame = TestGif.Frame(ByteArray(0), 0, 0)
        assertFailsWith<GifException> { GifReader(TestGif(0, 5, palette(2)).frame(frame).bytes()) }
        assertFailsWith<GifException> { GifReader(TestGif(5, 0, palette(2)).frame(frame).bytes()) }
        assertFailsWith<GifException> { GifReader(TestGif(5, 5, palette(2)).bytes()) } // no frames
    }

    @Test
    fun rejectsOversizedScreenAndFrames() {
        val frame = TestGif.Frame(byteArrayOf(0), 1, 1)
        val limits = GifLimits(maxDimension = 100, maxCanvasPixels = 50 * 50)
        assertFailsWith<GifException> { GifReader(TestGif(101, 1, palette(2)).frame(frame).bytes(), limits) }
        assertFailsWith<GifException> { GifReader(TestGif(60, 50, palette(2)).frame(frame).bytes(), limits) }
        GifReader(TestGif(50, 50, palette(2)).frame(frame).bytes(), limits)
        // A frame rectangle far bigger than the screen: rejected before its pixels are allocated.
        val huge = TestGif.Frame(ByteArray(0), 60000, 60000, rawData = byteArrayOf(0))
        assertFailsWith<GifException> { GifReader(TestGif(10, 10, palette(2)).frame(huge).bytes()) }
    }

    @Test
    fun rejectsTooManyFramesOrTooMuchWork() {
        val gif = TestGif(10, 10, palette(2)).apply {
            repeat(5) { frame(TestGif.Frame(ByteArray(100), 10, 10)) }
        }.bytes()
        assertEquals(5, GifReader(gif, GifLimits(maxFrames = 5)).frameCount)
        assertFailsWith<GifException> { GifReader(gif, GifLimits(maxFrames = 4)) }
        assertEquals(5, GifReader(gif, GifLimits(maxWorkPixels = 1000)).frameCount)
        assertFailsWith<GifException> { GifReader(gif, GifLimits(maxWorkPixels = 999)) }
    }

    @Test
    fun framesDecodeAgainOnEachCall() {
        val reader = GifReader(single(2, 1, byteArrayOf(1, 0), colors = 2))
        assertContentEquals(reader.all().single().indices, reader.all().single().indices)
    }

    private fun assertNotNullMissing(frame: GifFrame): ByteArray =
        frame.missing ?: throw AssertionError("expected a damaged frame")

    private fun subBlocks(data: ByteArray): ByteArray {
        val out = ArrayList<Byte>()
        data.toList().chunked(255).forEach { chunk ->
            out += chunk.size.toByte()
            out += chunk
        }
        out += 0
        return out.toByteArray()
    }
}
