package app.kisscut.keys.shared.core.gif

import app.kisscut.keys.shared.core.ImportLimits

/** A GIF that is broken or too big to decode safely. The caller stores the original bytes instead. */
class GifException(message: String) : Exception(message)

/** Safety limits for decoding; defaults from [ImportLimits]. Tests pass smaller ones. */
data class GifLimits(
    val maxDimension: Int = ImportLimits.MAX_DIMENSION,
    val maxCanvasPixels: Int = ImportLimits.GIF_MAX_CANVAS_PIXELS,
    val maxFrames: Int = ImportLimits.GIF_MAX_FRAMES,
    val maxWorkPixels: Long = ImportLimits.GIF_MAX_WORK_PIXELS,
)

/** How a frame is cleared before the next one draws (GIF89a disposal methods). */
enum class GifDisposal {
    /** Leave the frame in place (methods 0, 1 and the undefined 4-7). */
    KEEP,

    /** Clear the frame's rectangle to transparent (method 2; browsers ignore the background color). */
    CLEAR,

    /** Put back the canvas as it was before this frame drew (method 3). */
    RESTORE_PREVIOUS,
}

/**
 * One decoded frame, before compositing: [indices] are [width] x [height] palette indices in row order
 * (interlace already undone). Indices at or past [palette]'s size draw nothing, like [transparentIndex].
 */
class GifFrame(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    /** Opaque ARGB colors. */
    val palette: IntArray,
    /** -1 when the frame has no transparent color. */
    val transparentIndex: Int,
    val disposal: GifDisposal,
    /** Delay after this frame in hundredths of a second, as stored (players treat 0 and 1 as about 10). */
    val delayCs: Int,
    val indices: ByteArray,
    /** Only for damaged frames: nonzero where the pixel data was missing, so nothing draws there. */
    val missing: ByteArray? = null,
)

/**
 * Pure Kotlin GIF87a/GIF89a reader. The constructor walks the block structure without decoding pixels
 * and checks [limits], so a hostile file fails before any heavy work. [frames] then decodes one frame at
 * a time; draw them in order on a [GifCanvas] to get what a player shows.
 *
 * Damage rules, matching what browsers show:
 * - Missing trailer or junk after the last frame: the frames before it are kept.
 * - A frame with no color table or a bad LZW code size ends the GIF there.
 * - Truncated or broken pixel data leaves the rest of that frame undrawn; later frames still decode.
 * - The logical screen size is the canvas; frame parts outside it are clipped.
 * Throws [GifException] when there is no usable frame or a limit is exceeded.
 */
class GifReader(private val bytes: ByteArray, limits: GifLimits = GifLimits()) {
    val width: Int
    val height: Int

    /** From the NETSCAPE2.0 extension: 0 loops forever, null means no extension (play once). */
    val loopCount: Int?

    val frameCount: Int get() = headers.size

    private val headers: List<FrameHeader>

    init {
        val sig = if (bytes.size >= 6) bytes.decodeToString(0, 6) else ""
        if (sig != "GIF87a" && sig != "GIF89a") throw GifException("not a GIF")
        if (bytes.size < 13) throw GifException("truncated header")
        width = u16(6)
        height = u16(8)
        if (width == 0 || height == 0) throw GifException("empty logical screen")
        if (width > limits.maxDimension || height > limits.maxDimension) throw GifException("screen too large")
        val canvasPixels = width.toLong() * height
        if (canvasPixels > limits.maxCanvasPixels) throw GifException("screen too large")

        val screenFlags = u8(10)
        var pos = 13
        var globalPalette: Palette? = null
        if (screenFlags and 0x80 != 0) {
            val count = 1 shl ((screenFlags and 0x07) + 1)
            globalPalette = Palette(pos, count)
            pos += 3 * count
        }

        val found = ArrayList<FrameHeader>()
        var loops: Int? = null
        var gce = Gce.NONE
        var work = 0L
        scan@ while (pos < bytes.size) {
            when (u8(pos)) {
                0x21 -> {
                    if (pos + 1 >= bytes.size) break@scan
                    val label = u8(pos + 1)
                    val dataStart = pos + 2
                    when (label) {
                        0xF9 -> if (u8OrNull(dataStart) == 4 && dataStart + 4 < bytes.size) {
                            val flags = u8(dataStart + 1)
                            gce = Gce(
                                disposal = when ((flags shr 2) and 0x07) {
                                    2 -> GifDisposal.CLEAR
                                    3 -> GifDisposal.RESTORE_PREVIOUS
                                    else -> GifDisposal.KEEP
                                },
                                delayCs = u16(dataStart + 2),
                                transparentIndex = if (flags and 0x01 != 0) u8(dataStart + 4) else -1,
                            )
                        }
                        0xFF -> if (loops == null) loops = loopExtension(dataStart)
                    }
                    pos = skipSubBlocks(dataStart) ?: break@scan
                }
                0x2C -> {
                    if (pos + 10 >= bytes.size) break@scan
                    val left = u16(pos + 1)
                    val top = u16(pos + 3)
                    val w = u16(pos + 5)
                    val h = u16(pos + 7)
                    val flags = u8(pos + 9)
                    pos += 10
                    var palette = globalPalette
                    if (flags and 0x80 != 0) {
                        val count = 1 shl ((flags and 0x07) + 1)
                        palette = Palette(pos, count)
                        pos += 3 * count
                    }
                    if (palette == null || palette.offset + 3 * palette.count > bytes.size) break@scan
                    if (pos >= bytes.size) break@scan
                    val minCodeSize = u8(pos)
                    if (minCodeSize !in 2..8) break@scan
                    val framePixels = w.toLong() * h
                    if (framePixels > limits.maxCanvasPixels) throw GifException("frame too large")
                    if (found.size >= limits.maxFrames) throw GifException("too many frames")
                    work += canvasPixels + framePixels
                    if (work > limits.maxWorkPixels) throw GifException("too much to decode")
                    found += FrameHeader(left, top, w, h, flags and 0x40 != 0, palette, minCodeSize, pos + 1, gce)
                    gce = Gce.NONE // a control extension applies to the next image only
                    pos = skipSubBlocks(pos + 1) ?: break@scan
                }
                else -> break@scan // trailer (0x3B) or junk
            }
        }
        if (found.isEmpty()) throw GifException("no frames")
        headers = found
        loopCount = loops
    }

    /** Decodes lazily, one frame per [Iterator.next]; nothing else is kept. */
    fun frames(): Iterator<GifFrame> = headers.asSequence().map(::decode).iterator()

    private fun decode(h: FrameHeader): GifFrame {
        val pixels = ByteArray(h.width * h.height)
        val decoded = LzwDecoder(bytes, h.dataStart, h.minCodeSize).decodeInto(pixels)
        var missing: ByteArray? = null
        if (decoded < pixels.size) missing = ByteArray(pixels.size).also { it.fill(1, decoded, it.size) }
        if (h.interlaced) missing = missing?.let { deinterlace(it, h.width, h.height) }
        val indices = if (h.interlaced) deinterlace(pixels, h.width, h.height) else pixels
        val palette = IntArray(h.palette.count) {
            val p = h.palette.offset + 3 * it
            (0xFF shl 24) or (u8(p) shl 16) or (u8(p + 1) shl 8) or u8(p + 2)
        }
        return GifFrame(
            h.left, h.top, h.width, h.height, palette, h.gce.transparentIndex,
            h.gce.disposal, h.gce.delayCs, indices, missing,
        )
    }

    /** NETSCAPE2.0 / ANIMEXTS1.0 loop count, or null if this application extension is something else. */
    private fun loopExtension(dataStart: Int): Int? {
        if (u8OrNull(dataStart) != 11 || dataStart + 15 >= bytes.size) return null
        val id = bytes.decodeToString(dataStart + 1, dataStart + 12)
        if (id != "NETSCAPE2.0" && id != "ANIMEXTS1.0") return null
        if (u8(dataStart + 12) < 3 || u8(dataStart + 13) != 1) return null
        return u16(dataStart + 14)
    }

    /** Position after the sub-block terminator, or null when the blocks run past the end. */
    private fun skipSubBlocks(start: Int): Int? {
        var pos = start
        while (true) {
            val size = u8OrNull(pos) ?: return null
            pos += 1 + size
            if (size == 0) return pos
            if (pos > bytes.size) return null
        }
    }

    private fun u8(i: Int): Int = bytes[i].toInt() and 0xFF

    private fun u8OrNull(i: Int): Int? = if (i in bytes.indices) u8(i) else null

    private fun u16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)

    private class Palette(val offset: Int, val count: Int)

    private class Gce(val disposal: GifDisposal, val delayCs: Int, val transparentIndex: Int) {
        companion object {
            val NONE = Gce(GifDisposal.KEEP, 0, -1)
        }
    }

    private class FrameHeader(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val interlaced: Boolean,
        val palette: Palette,
        val minCodeSize: Int,
        val dataStart: Int,
        val gce: Gce,
    )

    companion object {
        /** Interlaced rows arrive in four passes: every 8th from 0, every 8th from 4, every 4th from 2, odd rows. */
        internal fun deinterlace(src: ByteArray, width: Int, height: Int): ByteArray {
            val out = ByteArray(src.size)
            var srcRow = 0
            for ((start, step) in INTERLACE_PASSES) {
                var row = start
                while (row < height) {
                    src.copyInto(out, row * width, srcRow * width, (srcRow + 1) * width)
                    srcRow++
                    row += step
                }
            }
            return out
        }

        private val INTERLACE_PASSES = listOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)
    }
}
