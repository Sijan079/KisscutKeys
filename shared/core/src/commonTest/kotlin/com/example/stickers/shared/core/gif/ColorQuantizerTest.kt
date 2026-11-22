package com.example.stickers.shared.core.gif

import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorQuantizerTest {
    private fun rgb(c: Int) = c and 0xFFFFFF

    @Test
    fun fewColorsAreKeptExactly() {
        val counts = mapOf(0x102030 to 5, 0xFFFFFF to 1, 0x000000 to 9)
        val result = ColorQuantizer(255).quantize(counts)
        assertEquals(setOf(0x102030, 0xFFFFFF, 0x000000), result.palette.map(::rgb).toSet())
        assertTrue(result.palette.all { it ushr 24 == 0xFF })
        for (c in counts.keys) assertEquals(c, rgb(result.palette[result.lookup.getValue(c)]))
    }

    @Test
    fun manyColorsReduceToTheLimitNearTheirOwnColor() {
        // 16 levels per channel: 4096 colors, evenly spread.
        val counts = HashMap<Int, Int>()
        for (r in 0 until 16) for (g in 0 until 16) for (b in 0 until 16) {
            counts[(r * 17 shl 16) or (g * 17 shl 8) or b * 17] = 1
        }
        val result = ColorQuantizer(255).quantize(counts)
        assertEquals(255, result.palette.size)
        var sum = 0.0
        for (c in counts.keys) {
            val p = result.palette[result.lookup.getValue(c)]
            var d = 0.0
            for (s in listOf(16, 8, 0)) {
                val diff = ((c shr s) and 0xFF) - ((p shr s) and 0xFF)
                d += diff * diff
            }
            sum += sqrt(d)
        }
        // 255 colors spread over the cube are about 40 apart; the nearest one is much closer than that.
        assertTrue(sum / counts.size < 30, "mean distance ${sum / counts.size}")
    }

    @Test
    fun aDominantColorGetsItsOwnEntry() {
        val r = Random(3)
        val counts = HashMap<Int, Int>()
        repeat(2000) { counts[r.nextInt(0x1000000)] = 1 }
        counts[0x3366CC] = 1_000_000
        val result = ColorQuantizer(255).quantize(counts)
        assertEquals(0x3366CC, rgb(result.palette[result.lookup.getValue(0x3366CC)]))
    }

    @Test
    fun sameInputSamePalette() {
        val r = Random(9)
        val counts = HashMap<Int, Int>()
        repeat(5000) { counts[r.nextInt(0x1000000)] = 1 + r.nextInt(5) }
        val a = ColorQuantizer(255).quantize(counts)
        val b = ColorQuantizer(255).quantize(HashMap(counts.entries.reversed().associate { it.key to it.value }))
        assertContentEquals(a.palette, b.palette)
        assertEquals(a.lookup, b.lookup)
    }
}
