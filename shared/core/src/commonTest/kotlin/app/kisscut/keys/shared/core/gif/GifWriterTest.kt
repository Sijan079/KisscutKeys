package app.kisscut.keys.shared.core.gif

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GifWriterTest {
    private fun palette(size: Int, seed: Int = 0) = Random(seed).let { r ->
        IntArray(size) { (0xFF shl 24) or r.nextInt(0x1000000) }
    }

    private fun frame(
        w: Int,
        h: Int,
        colors: Int,
        seed: Int = 1,
        left: Int = 0,
        top: Int = 0,
        disposal: GifDisposal = GifDisposal.KEEP,
        delayCs: Int = 10,
        transparentIndex: Int = -1,
        indices: ByteArray = Random(seed).let { r -> ByteArray(w * h) { r.nextInt(colors).toByte() } },
    ) = GifFrame(left, top, w, h, palette(colors, seed), transparentIndex, disposal, delayCs, indices)

    private fun roundTrip(width: Int, height: Int, loop: Int?, vararg frames: GifFrame): GifReader {
        val writer = GifWriter(width, height, loop)
        frames.forEach(writer::add)
        return GifReader(writer.finish())
    }

    private fun assertSameFrame(expected: GifFrame, actual: GifFrame) {
        assertEquals(
            listOf(expected.left, expected.top, expected.width, expected.height),
            listOf(actual.left, actual.top, actual.width, actual.height),
        )
        assertEquals(expected.disposal, actual.disposal)
        assertEquals(expected.delayCs, actual.delayCs)
        assertEquals(expected.transparentIndex, actual.transparentIndex)
        assertContentEquals(expected.palette, actual.palette.copyOf(expected.palette.size))
        assertContentEquals(expected.indices, actual.indices)
        assertNull(actual.missing)
    }

    @Test
    fun noiseRoundTripsThroughFullTablesAndClears() {
        val f = frame(300, 300, 256)
        assertSameFrame(f, roundTrip(300, 300, 0, f).frames().next())
    }

    @Test
    fun longRunsRoundTrip() {
        val f = frame(200, 100, 2, indices = ByteArray(200 * 100) { if (it < 15000) 1 else 0 })
        assertSameFrame(f, roundTrip(200, 100, 0, f).frames().next())
    }

    @Test
    fun everyPaletteSizeRoundTrips() {
        for (colors in 1..256) {
            val f = frame(13, 7, colors, seed = colors)
            assertSameFrame(f, roundTrip(13, 7, null, f).frames().next())
        }
    }

    @Test
    fun keepsFramePlacementDisposalDelayAndTransparency() {
        val frames = arrayOf(
            frame(10, 10, 4, seed = 1, delayCs = 3),
            frame(
                4, 3, 16, seed = 2, left = 5, top = 7,
                disposal = GifDisposal.CLEAR, delayCs = 250, transparentIndex = 9,
            ),
            frame(1, 1, 1, seed = 3, left = 9, top = 9, disposal = GifDisposal.RESTORE_PREVIOUS, delayCs = 0),
        )
        val reader = roundTrip(10, 10, 7, *frames)
        assertEquals(10 to 10, reader.width to reader.height)
        assertEquals(7, reader.loopCount)
        val read = reader.frames().asSequence().toList()
        assertEquals(frames.size, read.size)
        frames.zip(read.toTypedArray()).forEach { (e, a) -> assertSameFrame(e, a) }
    }

    @Test
    fun loopCountNullMeansNoExtension() {
        assertNull(roundTrip(2, 2, null, frame(2, 2, 2)).loopCount)
        assertEquals(0, roundTrip(2, 2, 0, frame(2, 2, 2)).loopCount)
    }

    @Test
    fun rejectsFramesThatDoNotFit() {
        val writer = GifWriter(10, 10, 0)
        assertFailsWith<IllegalArgumentException> { writer.add(frame(4, 4, 2, left = 7)) }
        assertFailsWith<IllegalArgumentException> { writer.add(frame(4, 4, 2, indices = ByteArray(3))) }
        assertFailsWith<IllegalArgumentException> {
            writer.add(GifFrame(0, 0, 1, 1, IntArray(0), -1, GifDisposal.KEEP, 0, ByteArray(1)))
        }
    }

    @Test
    fun shrunkFramesPlayTheSameAfterWriting() {
        // A 60 x 40 sky with a ball moving across and out, shrunk to 30 x 20, written, read back, replayed.
        val source = TestGif(60, 40).apply {
            for (f in 0 until 8) {
                val bx = f * 9
                val argb = IntArray(60 * 40) { p ->
                    if (p % 60 in bx until bx + 8 && p / 60 in 10 until 18) 0xFFFF4020.toInt() else 0
                }
                val indices = ByteArray(argb.size) { if (argb[it] != 0) 0 else 1 }
                val control = TestGif.Control(disposal = 2, delayCs = 4 + f, transparentIndex = 1)
                frame(TestGif.Frame(indices, 60, 40, localPalette = intArrayOf(0xFF4020, 0), control = control))
            }
        }.bytes()
        val shrinker = GifShrinker(GifReader(source), maxSide = 30)
        val shrunk = shrinker.frames().asSequence().toList()
        val writer = GifWriter(shrinker.width, shrinker.height, shrinker.loopCount)
        shrunk.forEach(writer::add)
        val written = GifReader(writer.finish())

        val before = GifCanvas(shrinker.width, shrinker.height)
        val after = GifCanvas(written.width, written.height)
        val read = written.frames().asSequence().toList()
        assertEquals(shrunk.map { it.delayCs }, read.map { it.delayCs })
        shrunk.zip(read).forEachIndexed { i, (a, b) ->
            assertContentEquals(before.draw(a), after.draw(b), "frame $i")
        }
    }
}
