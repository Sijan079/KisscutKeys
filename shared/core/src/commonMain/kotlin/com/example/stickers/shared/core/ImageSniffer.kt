package com.example.stickers.shared.core

/** The image formats the app stores. The extension names files; the MIME type goes in the row. */
enum class ImageType(val mimeType: String, val extension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif"),
}

/**
 * What the header says. [mayHaveAlpha] only means the format can carry transparency here; the real
 * answer comes from the pixels (see [hasTransparentPixel]).
 */
data class ImageInfo(
    val type: ImageType,
    val width: Int,
    val height: Int,
    val isAnimated: Boolean,
    val mayHaveAlpha: Boolean,
)

/**
 * Reads the real type from magic bytes; file names and declared MIME types are never trusted.
 * Returns null for anything that is not a well-formed PNG, JPEG, WebP or GIF header.
 */
object ImageSniffer {
    fun sniff(bytes: ByteArray): ImageInfo? {
        val r = Bytes(bytes)
        val info = when {
            r.startsWith(PNG_SIGNATURE) -> png(r)
            r.ascii(0, 4) == "GIF8" && (r.ascii(0, 6) == "GIF87a" || r.ascii(0, 6) == "GIF89a") -> gif(r)
            r.ascii(0, 4) == "RIFF" && r.ascii(8, 4) == "WEBP" -> webp(r)
            r.u8(0) == 0xFF && r.u8(1) == 0xD8 && r.u8(2) == 0xFF -> jpeg(r)
            else -> null
        }
        return info?.takeIf { it.width > 0 && it.height > 0 }
    }

    // PNG: IHDR is always the first chunk. Color types 4 and 6 carry alpha; a tRNS chunk adds it to others.
    private fun png(r: Bytes): ImageInfo? {
        if (r.ascii(12, 4) != "IHDR") return null
        val width = r.int32Be(16) ?: return null
        val height = r.int32Be(20) ?: return null
        val colorType = r.u8(25) ?: return null
        var alpha = colorType == 4 || colorType == 6
        var pos = 8
        while (!alpha) {
            val length = r.int32Be(pos) ?: break
            val type = r.ascii(pos + 4, 4) ?: break
            if (type == "tRNS") alpha = true
            if (type == "IDAT" || type == "IEND" || length < 0) break
            pos += 12 + length
        }
        // Animated PNG (acTL) is stored as a still PNG: Android decoders show its first frame.
        return ImageInfo(ImageType.PNG, width, height, isAnimated = false, mayHaveAlpha = alpha)
    }

    // GIF: walk the block structure and count image descriptors; stop at two.
    private fun gif(r: Bytes): ImageInfo? {
        val width = r.u16Le(6) ?: return null
        val height = r.u16Le(8) ?: return null
        val packed = r.u8(10) ?: return null
        var pos = 13
        if (packed and 0x80 != 0) pos += 3 * (1 shl ((packed and 0x07) + 1))
        var frames = 0
        var transparent = false
        loop@ while (frames < 2) {
            when (r.u8(pos) ?: break@loop) {
                0x21 -> { // extension: label, then sub-blocks
                    val label = r.u8(pos + 1) ?: break@loop
                    if (label == 0xF9) {
                        val flags = r.u8(pos + 3) ?: break@loop
                        if (flags and 0x01 != 0) transparent = true
                    }
                    pos = r.skipSubBlocks(pos + 2) ?: break@loop
                }
                0x2C -> { // image descriptor (10 bytes), optional local table, LZW size, sub-blocks
                    val local = r.u8(pos + 9) ?: break@loop
                    frames++
                    pos += 10
                    if (local and 0x80 != 0) pos += 3 * (1 shl ((local and 0x07) + 1))
                    pos = r.skipSubBlocks(pos + 1) ?: break@loop
                }
                else -> break@loop // trailer (0x3B) or junk
            }
        }
        if (frames == 0) return null
        return ImageInfo(ImageType.GIF, width, height, isAnimated = frames > 1, mayHaveAlpha = transparent)
    }

    // JPEG: walk the marker segments to the first start-of-frame (SOF0-SOF15 except DHT, JPG and DAC).
    // Width and height sit there; stopping at start-of-scan or the end means no frame header.
    private fun jpeg(r: Bytes): ImageInfo? {
        var pos = 2
        while (true) {
            if (r.u8(pos) != 0xFF) return null
            val marker = r.u8(pos + 1) ?: return null
            when {
                marker == 0xFF -> pos += 1 // fill byte
                marker == 0x01 || marker in 0xD0..0xD7 -> pos += 2 // no length
                marker == 0xD9 || marker == 0xDA -> return null // end or scan before any frame
                marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC -> {
                    val height = r.u16Be(pos + 5) ?: return null
                    val width = r.u16Be(pos + 7) ?: return null
                    return ImageInfo(ImageType.JPEG, width, height, isAnimated = false, mayHaveAlpha = false)
                }
                else -> {
                    val length = r.u16Be(pos + 2) ?: return null
                    if (length < 2) return null
                    pos += 2 + length
                }
            }
        }
    }

    private fun webp(r: Bytes): ImageInfo? = when (r.ascii(12, 4)) {
        "VP8 " -> {
            if (r.u8(23) != 0x9D || r.u8(24) != 0x01 || r.u8(25) != 0x2A) {
                null
            } else {
                val w = r.u16Le(26)?.and(0x3FFF)
                val h = r.u16Le(28)?.and(0x3FFF)
                if (w == null || h == null) null else ImageInfo(ImageType.WEBP, w, h, false, mayHaveAlpha = false)
            }
        }
        "VP8L" -> {
            val bits = r.int32Le(21)
            if (r.u8(20) != 0x2F || bits == null) {
                null
            } else {
                val w = (bits and 0x3FFF) + 1
                val h = ((bits ushr 14) and 0x3FFF) + 1
                ImageInfo(ImageType.WEBP, w, h, isAnimated = false, mayHaveAlpha = (bits ushr 28) and 1 == 1)
            }
        }
        "VP8X" -> {
            val flags = r.u8(20)
            val w = r.u24Le(24)
            val h = r.u24Le(27)
            if (flags == null || w == null || h == null) {
                null
            } else {
                ImageInfo(
                    ImageType.WEBP,
                    w + 1,
                    h + 1,
                    isAnimated = flags and 0x02 != 0,
                    mayHaveAlpha = flags and 0x10 != 0,
                )
            }
        }
        else -> null
    }

    private val PNG_SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Bounds-checked reads; every accessor returns null past the end. */
    private class Bytes(private val b: ByteArray) {
        fun u8(i: Int): Int? = if (i in b.indices) b[i].toInt() and 0xFF else null

        fun u16Le(i: Int): Int? {
            val lo = u8(i) ?: return null
            val hi = u8(i + 1) ?: return null
            return lo or (hi shl 8)
        }

        fun u16Be(i: Int): Int? {
            val hi = u8(i) ?: return null
            val lo = u8(i + 1) ?: return null
            return (hi shl 8) or lo
        }

        fun u24Le(i: Int): Int? {
            val lo = u16Le(i) ?: return null
            val hi = u8(i + 2) ?: return null
            return lo or (hi shl 16)
        }

        fun int32Le(i: Int): Int? {
            val lo = u16Le(i) ?: return null
            val hi = u16Le(i + 2) ?: return null
            return lo or (hi shl 16)
        }

        fun int32Be(i: Int): Int? {
            var v = 0
            for (k in 0 until 4) v = (v shl 8) or (u8(i + k) ?: return null)
            return v
        }

        fun ascii(i: Int, n: Int): String? {
            if (i < 0 || i + n > b.size) return null
            return CharArray(n) { (b[i + it].toInt() and 0xFF).toChar() }.concatToString()
        }

        fun startsWith(sig: IntArray): Boolean = sig.indices.all { u8(it) == sig[it] }

        /** Skips GIF data sub-blocks starting at [start]; returns the position after the terminator. */
        fun skipSubBlocks(start: Int): Int? {
            var pos = start
            while (true) {
                val size = u8(pos) ?: return null
                pos += 1
                if (size == 0) return pos
                pos += size
            }
        }
    }
}
