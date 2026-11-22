package app.kisscut.keys.shared.core.gif

/**
 * Turns a GIF into the frames of a smaller one, ready for a writer: each frame is composited at full
 * size, scaled to [width] x [height] (longest side [maxSide]) and then reduced to what a player needs
 * to redraw.
 *
 * - A pixel whose scaled color did not change since it was last drawn is not redrawn (it gets the
 *   transparent "keep" index), so still areas never flicker and the colors go to what moved.
 * - Each frame's rectangle is the box around the pixels it redraws, plus any pixels the next frame
 *   needs cleared (opaque now, transparent next); then the frame gets [GifDisposal.CLEAR].
 * - Each frame gets its own palette of at most 255 colors (exact when the redrawn pixels have no more)
 *   and the transparent index after them.
 *
 * Holds the scaled current and next frame plus one canvas at full size; never the whole GIF.
 */
class GifShrinker(private val reader: GifReader, maxSide: Int) {
    val width: Int
    val height: Int
    val frameCount: Int get() = reader.frameCount
    val loopCount: Int? get() = reader.loopCount

    init {
        val (w, h) = GifScaler.fit(reader.width, reader.height, maxSide)
        width = w
        height = h
    }

    /** One output frame per input frame, same delays, produced one at a time. */
    fun frames(): Iterator<GifFrame> = iterator {
        val canvas = GifCanvas(reader.width, reader.height)
        val scaler = GifScaler(reader.width, reader.height, width, height)
        val source = reader.frames()
        // What each output pixel shows in a player right now, as the scaled color it stands for.
        val shown = IntArray(width * height)
        var current = source.next()
        var target = scaler.scale(canvas.draw(current))
        while (true) {
            val nextFrame = if (source.hasNext()) source.next() else null
            val next = nextFrame?.let { scaler.scale(canvas.draw(it)) }
            yield(encode(target, next, shown, current.delayCs))
            current = nextFrame ?: break
            target = next ?: break
        }
    }

    private fun encode(target: IntArray, next: IntArray?, shown: IntArray, delayCs: Int): GifFrame {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var clear = false
        val counts = HashMap<Int, Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = y * width + x
                val t = target[p]
                // A redrawn pixel is opaque: the previous frame cleared any pixel that turns transparent.
                val redraw = shown[p] != t
                val toClear = next != null && t != 0 && next[p] == 0
                if (redraw) {
                    val rgb = t and 0xFFFFFF
                    counts[rgb] = (counts[rgb] ?: 0) + 1
                }
                if (toClear) clear = true
                if (redraw || toClear) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) { // nothing to redraw: a 1x1 frame that draws nothing keeps the delay
            left = 0
            top = 0
            right = 0
            bottom = 0
        }
        val w = right - left + 1
        val h = bottom - top + 1
        val quantized = ColorQuantizer(MAX_COLORS).quantize(counts)
        val keep = quantized.palette.size
        val indices = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = (top + y) * width + left + x
                val t = target[p]
                indices[y * w + x] = if (shown[p] != t) {
                    shown[p] = t
                    quantized.lookup.getValue(t and 0xFFFFFF).toByte()
                } else {
                    keep.toByte()
                }
            }
        }
        if (clear) {
            for (y in top..bottom) shown.fill(0, y * width + left, y * width + right + 1)
        }
        return GifFrame(
            left = left,
            top = top,
            width = w,
            height = h,
            palette = quantized.palette + 0,
            transparentIndex = keep,
            disposal = if (clear) GifDisposal.CLEAR else GifDisposal.KEEP,
            delayCs = delayCs,
            indices = indices,
        )
    }

    private companion object {
        /** One of GIF's 256 palette slots is the transparent "keep" index. */
        const val MAX_COLORS = 255
    }
}
