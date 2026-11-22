package com.example.stickers.shared.core.gif

/**
 * Builds GIF bytes for reader tests, with its own LZW encoder (independent of the reader's decoder).
 * Palettes are RGB ints; their size must be a power of two from 2 to 256.
 */
class TestGif(
    private val width: Int,
    private val height: Int,
    private val globalPalette: IntArray? = null,
    private val loopId: String? = null,
    private val loopCount: Int = 0,
) {
    class Frame(
        val indices: ByteArray,
        val width: Int,
        val height: Int,
        val left: Int = 0,
        val top: Int = 0,
        val localPalette: IntArray? = null,
        /** Null writes no control extension. */
        val control: Control? = Control(),
        val interlaced: Boolean = false,
        val minCodeSize: Int? = null,
        /** Replaces the LZW sub-blocks (including the terminator) when set. */
        val rawData: ByteArray? = null,
        val startWithClear: Boolean = true,
        val clearWhenFull: Boolean = true,
    )

    class Control(val disposal: Int = 0, val delayCs: Int = 0, val transparentIndex: Int = -1)

    private val frames = ArrayList<Frame>()

    fun frame(frame: Frame): TestGif = apply { frames += frame }

    fun bytes(trailer: Boolean = true): ByteArray {
        val out = Out()
        out.ascii("GIF89a")
        out.u16(width)
        out.u16(height)
        out.u8(if (globalPalette != null) 0x80 or (sizeBits(globalPalette) - 1) else 0)
        out.u8(0)
        out.u8(0)
        globalPalette?.let { out.palette(it) }
        loopId?.let {
            out.u8(0x21)
            out.u8(0xFF)
            out.u8(11)
            out.ascii(it)
            out.u8(3)
            out.u8(1)
            out.u16(loopCount)
            out.u8(0)
        }
        for (f in frames) {
            f.control?.let {
                out.u8(0x21)
                out.u8(0xF9)
                out.u8(4)
                out.u8((it.disposal shl 2) or (if (it.transparentIndex >= 0) 1 else 0))
                out.u16(it.delayCs)
                out.u8(maxOf(it.transparentIndex, 0))
                out.u8(0)
            }
            out.u8(0x2C)
            out.u16(f.left)
            out.u16(f.top)
            out.u16(f.width)
            out.u16(f.height)
            var flags = if (f.interlaced) 0x40 else 0
            if (f.localPalette != null) flags = flags or 0x80 or (sizeBits(f.localPalette) - 1)
            out.u8(flags)
            f.localPalette?.let { out.palette(it) }
            val palette = f.localPalette ?: globalPalette
            val minCodeSize = f.minCodeSize ?: maxOf(2, palette?.let(::sizeBits) ?: 8)
            out.u8(minCodeSize)
            if (f.rawData != null) {
                out.bytes(f.rawData)
            } else {
                val rows = if (f.interlaced) interlace(f.indices, f.width, f.height) else f.indices
                out.subBlocks(lzw(rows, minCodeSize, f.startWithClear, f.clearWhenFull))
            }
        }
        if (trailer) out.u8(0x3B)
        return out.toByteArray()
    }

    companion object {
        private fun sizeBits(palette: IntArray): Int {
            var bits = 1
            while ((1 shl bits) < palette.size) bits++
            require(1 shl bits == palette.size) { "palette size must be a power of two" }
            return bits
        }

        /** Reorders rows into the four GIF interlace passes. */
        fun interlace(rows: ByteArray, width: Int, height: Int): ByteArray {
            val out = ByteArray(rows.size)
            var dst = 0
            for ((start, step) in listOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)) {
                var y = start
                while (y < height) {
                    rows.copyInto(out, dst * width, y * width, (y + 1) * width)
                    dst++
                    y += step
                }
            }
            return out
        }

        /** Plain GIF LZW, codes packed least significant bit first. */
        fun lzw(
            data: ByteArray,
            minCodeSize: Int,
            startWithClear: Boolean = true,
            clearWhenFull: Boolean = true,
        ): ByteArray {
            val bits = BitPacker()
            val clear = 1 shl minCodeSize
            val end = clear + 1
            var codeSize = minCodeSize + 1
            var next = clear + 2
            val dict = HashMap<Int, Int>()
            if (startWithClear) bits.put(clear, codeSize)
            if (data.isEmpty()) {
                bits.put(end, codeSize)
                return bits.toByteArray()
            }
            var current = data[0].toInt() and 0xFF
            for (i in 1 until data.size) {
                val b = data[i].toInt() and 0xFF
                val key = (current shl 8) or b
                val known = dict[key]
                if (known != null) {
                    current = known
                    continue
                }
                bits.put(current, codeSize)
                if (next < 4096) {
                    dict[key] = next++
                    if (next > (1 shl codeSize) && codeSize < 12) codeSize++
                } else if (clearWhenFull) {
                    bits.put(clear, codeSize)
                    dict.clear()
                    next = clear + 2
                    codeSize = minCodeSize + 1
                }
                current = b
            }
            bits.put(current, codeSize)
            bits.put(end, codeSize)
            return bits.toByteArray()
        }

        /** Packs codes of the given widths into bytes, least significant bit first. */
        class BitPacker {
            private val out = ArrayList<Byte>()
            private var buffer = 0
            private var count = 0

            fun put(code: Int, size: Int) {
                buffer = buffer or (code shl count)
                count += size
                while (count >= 8) {
                    out += buffer.toByte()
                    buffer = buffer ushr 8
                    count -= 8
                }
            }

            fun toByteArray(): ByteArray {
                if (count > 0) out += buffer.toByte()
                count = 0
                return out.toByteArray()
            }
        }
    }

    private class Out {
        private val data = ArrayList<Byte>()

        fun u8(v: Int) {
            data += v.toByte()
        }

        fun u16(v: Int) {
            u8(v and 0xFF)
            u8(v shr 8)
        }

        fun ascii(s: String) = s.forEach { u8(it.code) }

        fun bytes(b: ByteArray) = b.forEach { data += it }

        fun palette(p: IntArray) = p.forEach {
            u8(it shr 16 and 0xFF)
            u8(it shr 8 and 0xFF)
            u8(it and 0xFF)
        }

        /** Splits [b] into sub-blocks of at most 255 bytes, then the terminator. */
        fun subBlocks(b: ByteArray) {
            var i = 0
            while (i < b.size) {
                val n = minOf(255, b.size - i)
                u8(n)
                for (k in i until i + n) data += b[k]
                i += n
            }
            u8(0)
        }

        fun toByteArray() = data.toByteArray()
    }
}
