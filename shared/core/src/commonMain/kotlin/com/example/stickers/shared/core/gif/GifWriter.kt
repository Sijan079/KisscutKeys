package com.example.stickers.shared.core.gif

/**
 * Writes a GIF89a frame by frame, so only the output bytes and one frame are in memory. Each frame
 * is written as given (its rectangle, local palette, transparent index, disposal and delay); the
 * changed-area cropping is [GifShrinker]'s job. [loopCount] null writes no loop extension (play once),
 * 0 loops forever.
 */
class GifWriter(private val width: Int, private val height: Int, loopCount: Int?) {
    private val out = ByteSink()

    init {
        require(width in 1..0xFFFF && height in 1..0xFFFF)
        out.ascii("GIF89a")
        out.u16(width)
        out.u16(height)
        out.u8(0) // no global color table; every frame has its own
        out.u8(0) // background color index (players ignore it)
        out.u8(0) // no pixel aspect ratio
        if (loopCount != null) {
            out.u8(0x21)
            out.u8(0xFF)
            out.u8(11)
            out.ascii("NETSCAPE2.0")
            out.u8(3)
            out.u8(1)
            out.u16(loopCount.coerceIn(0, 0xFFFF))
            out.u8(0)
        }
    }

    fun add(frame: GifFrame) {
        require(frame.palette.size in 1..256) { "palette must have 1 to 256 colors" }
        require(frame.left >= 0 && frame.top >= 0 && frame.width >= 1 && frame.height >= 1)
        require(frame.left + frame.width <= width && frame.top + frame.height <= height) { "frame outside canvas" }
        require(frame.indices.size == frame.width * frame.height)

        val transparent = frame.transparentIndex in frame.palette.indices
        out.u8(0x21)
        out.u8(0xF9)
        out.u8(4)
        val disposal = when (frame.disposal) {
            GifDisposal.KEEP -> 1
            GifDisposal.CLEAR -> 2
            GifDisposal.RESTORE_PREVIOUS -> 3
        }
        out.u8((disposal shl 2) or (if (transparent) 1 else 0))
        out.u16(frame.delayCs.coerceIn(0, 0xFFFF))
        out.u8(if (transparent) frame.transparentIndex else 0)
        out.u8(0)

        var bits = 1
        while ((1 shl bits) < frame.palette.size) bits++
        out.u8(0x2C)
        out.u16(frame.left)
        out.u16(frame.top)
        out.u16(frame.width)
        out.u16(frame.height)
        out.u8(0x80 or (bits - 1)) // local color table, not interlaced
        for (i in 0 until (1 shl bits)) {
            val c = if (i < frame.palette.size) frame.palette[i] else 0
            out.u8((c shr 16) and 0xFF)
            out.u8((c shr 8) and 0xFF)
            out.u8(c and 0xFF)
        }
        val minCodeSize = maxOf(2, bits)
        out.u8(minCodeSize)
        LzwEncoder(minCodeSize).encode(frame.indices, out)
    }

    /** Adds the trailer and returns the file. The writer cannot be used after this. */
    fun finish(): ByteArray {
        out.u8(0x3B)
        return out.toByteArray()
    }
}

/** A growable byte buffer. */
internal class ByteSink(initial: Int = 64 * 1024) {
    private var buf = ByteArray(initial)
    var size = 0
        private set

    fun u8(v: Int) {
        if (size == buf.size) buf = buf.copyOf(buf.size * 2)
        buf[size++] = v.toByte()
    }

    fun u16(v: Int) {
        u8(v and 0xFF)
        u8((v shr 8) and 0xFF)
    }

    fun ascii(s: String) = s.forEach { u8(it.code) }

    fun bytes(src: ByteArray, from: Int, count: Int) {
        while (size + count > buf.size) buf = buf.copyOf(buf.size * 2)
        src.copyInto(buf, size, from, from + count)
        size += count
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}
