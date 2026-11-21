package com.example.stickers.shared.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlphaTest {
    @Test
    fun fullyOpaquePixelsHaveNoAlpha() {
        assertFalse(hasTransparentPixel(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF123456.toInt())))
    }

    @Test
    fun anyPartlyTransparentPixelCounts() {
        assertTrue(hasTransparentPixel(intArrayOf(0xFF000000.toInt(), 0xFE000000.toInt())))
        assertTrue(hasTransparentPixel(intArrayOf(0x00000000)))
    }

    @Test
    fun emptyIsOpaque() {
        assertFalse(hasTransparentPixel(IntArray(0)))
    }
}
