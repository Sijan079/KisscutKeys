package com.example.stickers.shared.core.gif

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GifScalerTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun fitKeepsAspectWithTheLongestSideAtTheLimit() {
        assertEquals(512 to 307, GifScaler.fit(1000, 600, 512))
        assertEquals(307 to 512, GifScaler.fit(600, 1000, 512))
        assertEquals(512 to 1, GifScaler.fit(5000, 1, 512))
        assertEquals(512 to 512, GifScaler.fit(513, 513, 512))
        assertEquals(512 to 300, GifScaler.fit(512, 300, 512))
    }

    @Test
    fun solidColorStaysExact() {
        val out = GifScaler(7, 5, 3, 2).scale(IntArray(35) { 0xFF123456.toInt() })
        assertTrue(out.all { it == 0xFF123456.toInt() })
    }

    @Test
    fun transparentPixelsAddNoDarkHalo() {
        // 3 -> 2: the right output pixel covers half of R and all of the other R.
        assertContentEquals(intArrayOf(0, red), GifScaler(3, 1, 2, 1).scale(intArrayOf(0, red, red)))
        // Two thirds opaque: the color is the mean of R and B only, not pulled toward black.
        assertContentEquals(intArrayOf(0xFF800080.toInt()), GifScaler(3, 1, 1, 1).scale(intArrayOf(red, blue, 0)))
        // One third opaque: transparent.
        assertContentEquals(intArrayOf(0), GifScaler(3, 1, 1, 1).scale(intArrayOf(red, 0, 0)))
    }

    @Test
    fun matchesASupersampledAreaAverage() {
        val sw = 97
        val sh = 61
        val ow = 50
        val oh = 31
        val r = Random(5)
        val src = IntArray(sw * sh) { if (r.nextInt(4) == 0) 0 else (0xFF shl 24) or r.nextInt(0x1000000) }
        val out = GifScaler(sw, sh, ow, oh).scale(src)
        val n = 40 // samples per output pixel side
        var err = 0.0
        var compared = 0
        for (oy in 0 until oh) for (ox in 0 until ow) {
            var a = 0.0
            val c = DoubleArray(3)
            for (j in 0 until n) for (i in 0 until n) {
                val sx = ((ox + (i + 0.5) / n) * sw / ow).toInt()
                val sy = ((oy + (j + 0.5) / n) * sh / oh).toInt()
                val p = src[sy * sw + sx]
                if (p == 0) continue
                a += 1.0
                for (ch in 0..2) c[ch] += (p shr (16 - 8 * ch)) and 0xFF
            }
            val alpha = a / (n * n)
            val got = out[oy * ow + ox]
            if (abs(alpha - 0.5) < 0.05) continue // samples can't settle a near-half pixel
            assertEquals(alpha >= 0.5, got != 0, "opacity at $ox,$oy")
            if (got == 0) continue
            for (ch in 0..2) err += abs(c[ch] / a - ((got shr (16 - 8 * ch)) and 0xFF))
            compared++
        }
        assertTrue(compared > 1000)
        assertTrue(err / (3 * compared) < 1.5, "mean channel error ${err / (3 * compared)}")
    }
}
