package com.example.stickers.shared.core.gif

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GifShrinkerTest {
    private val sky = 0xFF3080FF.toInt()
    private val ball = 0xFFFF4020.toInt()

    /** Encodes full-canvas ARGB frames (0 = transparent) as a GIF, each with its own palette, cleared after showing. */
    private fun gif(
        width: Int,
        height: Int,
        frames: List<IntArray>,
        delays: List<Int>? = null,
        loop: Int? = 0,
    ): ByteArray {
        val out = TestGif(width, height, loopId = loop?.let { "NETSCAPE2.0" }, loopCount = loop ?: 0)
        frames.forEachIndexed { i, argb ->
            val colors = argb.filter { it != 0 }.distinct()
            var size = 2
            while (size < colors.size + 1) size *= 2
            require(size <= 256)
            val palette = IntArray(size) { if (it < colors.size) colors[it] and 0xFFFFFF else 0 }
            val transparent = size - 1
            val index = colors.withIndex().associate { it.value to it.index }
            val indices = ByteArray(argb.size) { p -> (index[argb[p]] ?: transparent).toByte() }
            val control = TestGif.Control(disposal = 2, delayCs = delays?.get(i) ?: 10, transparentIndex = transparent)
            out.frame(TestGif.Frame(indices, width, height, localPalette = palette, control = control))
        }
        return out.bytes()
    }

    /** What a player shows for each frame of the source, scaled independently of the shrinker's choices. */
    private fun expectedFrames(bytes: ByteArray, w: Int, h: Int): List<IntArray> {
        val reader = GifReader(bytes)
        val canvas = GifCanvas(reader.width, reader.height)
        val scaler = GifScaler(reader.width, reader.height, w, h)
        return reader.frames().asSequence().map { scaler.scale(canvas.draw(it)) }.toList()
    }

    /** Plays the shrunk frames on a fresh canvas. */
    private fun replay(shrinker: GifShrinker): Pair<List<GifFrame>, List<IntArray>> {
        val canvas = GifCanvas(shrinker.width, shrinker.height)
        val frames = shrinker.frames().asSequence().toList()
        return frames to frames.map { canvas.draw(it).copyOf() }
    }

    /** A 40 x 20 sky with a 6 x 6 ball moving right 4px per frame; the ball leaves the canvas at the end. */
    private fun bouncing(transparentSky: Boolean): List<IntArray> = (0 until 12).map { f ->
        IntArray(40 * 20) { p ->
            val x = p % 40
            val y = p / 40
            val bx = f * 4
            if (x in bx until bx + 6 && y in 7 until 13) ball else if (transparentSky) 0 else sky
        }
    }

    @Test
    fun replayMatchesTheScaledSourceExactlyWhenColorsFit() {
        for (transparentSky in listOf(false, true)) {
            val bytes = gif(40, 20, bouncing(transparentSky), delays = (1..12).toList(), loop = 3)
            val shrinker = GifShrinker(GifReader(bytes), maxSide = 16)
            assertEquals(16 to 8, shrinker.width to shrinker.height)
            assertEquals(3, shrinker.loopCount)
            val (frames, shown) = replay(shrinker)
            assertEquals((1..12).toList(), frames.map { it.delayCs })
            val expected = expectedFrames(bytes, 16, 8)
            assertEquals(expected.size, shown.size)
            expected.zip(shown).forEachIndexed { i, (e, s) -> assertContentEquals(e, s, "frame $i") }
            assertTrue(frames.all { it.palette.size <= 256 && it.transparentIndex == it.palette.size - 1 })
        }
    }

    @Test
    fun stillBackgroundIsNotRedrawn() {
        val (frames, _) = replay(GifShrinker(GifReader(gif(40, 20, bouncing(false))), maxSide = 16))
        assertEquals(16 * 8, frames[0].width * frames[0].height)
        for (f in frames.drop(1)) {
            assertTrue(f.width * f.height <= 6 * 4, "frame redraws only around the ball: ${f.width}x${f.height}")
            assertEquals(GifDisposal.KEEP, f.disposal)
        }
    }

    @Test
    fun pixelsThatTurnTransparentAreClearedByThePreviousFrame() {
        val bytes = gif(40, 20, bouncing(transparentSky = true))
        val (frames, shown) = replay(GifShrinker(GifReader(bytes), maxSide = 16))
        val expected = expectedFrames(bytes, 16, 8)
        for (i in frames.indices) {
            val turnsTransparent = i + 1 < expected.size && expected[i].indices.any {
                expected[i][it] != 0 && expected[i + 1][it] == 0
            }
            assertEquals(if (turnsTransparent) GifDisposal.CLEAR else GifDisposal.KEEP, frames[i].disposal, "frame $i")
        }
        assertTrue(frames.count { it.disposal == GifDisposal.CLEAR } >= 5)
        assertTrue(shown.last().all { it == 0 }, "the ball has left the canvas")
    }

    @Test
    fun spriteThatVanishesLeavesNothingBehind() {
        val withBall = IntArray(30 * 30) { p -> if (p % 30 in 10 until 20 && p / 30 in 10 until 20) ball else 0 }
        val bytes = gif(30, 30, listOf(withBall, IntArray(30 * 30), withBall))
        val (frames, shown) = replay(GifShrinker(GifReader(bytes), maxSide = 15))
        assertEquals(GifDisposal.CLEAR, frames[0].disposal)
        assertTrue(shown[1].all { it == 0 })
        assertContentEquals(expectedFrames(bytes, 15, 15)[2], shown[2])
    }

    @Test
    fun unchangedFrameIsATinyFrameThatKeepsItsDelay() {
        val still = IntArray(20 * 20) { sky }
        val bytes = gif(20, 20, listOf(still, still), delays = listOf(5, 70))
        val (frames, shown) = replay(GifShrinker(GifReader(bytes), maxSide = 10))
        assertEquals(1 to 1, frames[1].width to frames[1].height)
        assertEquals(frames[1].transparentIndex, frames[1].indices[0].toInt() and 0xFF)
        assertEquals(70, frames[1].delayCs)
        assertContentEquals(shown[0], shown[1])
    }

    @Test
    fun manyColorsStayClose() {
        // Each frame is noise from its own 255 random colors; averaging 2 x 2 blocks makes about 1000
        // colors per scaled frame, so the quantizer has to cut them to 255.
        val frames = (0 until 3).map { f ->
            val r = Random(f)
            val colors = IntArray(255) { (0xFF shl 24) or r.nextInt(0x1000000) }
            IntArray(64 * 64) { colors[r.nextInt(255)] }
        }
        val bytes = gif(64, 64, frames)
        val (out, shown) = replay(GifShrinker(GifReader(bytes), maxSide = 32))
        assertTrue(out.all { it.palette.size == 256 }, "every frame needed the full palette")
        expectedFrames(bytes, 32, 32).zip(shown).forEach { (e, s) ->
            var err = 0L
            for (p in e.indices) {
                for (sh in listOf(16, 8, 0)) err += abs(((e[p] shr sh) and 0xFF) - ((s[p] shr sh) and 0xFF))
            }
            assertTrue(err / (3.0 * e.size) < 12.0, "mean channel error ${err / (3.0 * e.size)}")
        }
    }

    @Test
    fun oddSizesScale() {
        val wide = gif(1000, 600, listOf(IntArray(1000 * 600) { sky }))
        val shrinker = GifShrinker(GifReader(wide), maxSide = 512)
        assertEquals(512 to 307, shrinker.width to shrinker.height)
        assertTrue(replay(shrinker).second.single().all { it == sky })

        val line = GifShrinker(GifReader(gif(1000, 1, listOf(IntArray(1000) { ball }))), maxSide = 512)
        assertEquals(512 to 1, line.width to line.height)
        assertTrue(replay(line).second.single().all { it == ball })
    }
}
