package app.kisscut.keys.shared.core.gif

/**
 * Composites [GifFrame]s the way a player shows them. The canvas starts transparent (players ignore
 * the background color), the previous frame's disposal runs before the next frame draws, and pixels
 * are either fully opaque or fully transparent (0).
 */
class GifCanvas(val width: Int, val height: Int) {
    /** ARGB, row by row. [draw] changes it in place; copy it to keep a frame. */
    val pixels = IntArray(width * height)

    private var saved: IntArray? = null
    private var pendingDisposal = GifDisposal.KEEP
    private var pendingLeft = 0
    private var pendingTop = 0
    private var pendingRight = 0
    private var pendingBottom = 0

    /** Disposes of the previous frame, draws [frame] and returns [pixels]. */
    fun draw(frame: GifFrame): IntArray {
        when (pendingDisposal) {
            GifDisposal.KEEP -> Unit
            GifDisposal.CLEAR -> for (y in pendingTop until pendingBottom) {
                pixels.fill(0, y * width + pendingLeft, y * width + pendingRight)
            }
            GifDisposal.RESTORE_PREVIOUS -> saved?.copyInto(pixels)
        }
        if (frame.disposal == GifDisposal.RESTORE_PREVIOUS) {
            val copy = saved ?: IntArray(pixels.size).also { saved = it }
            pixels.copyInto(copy)
        }

        // The part of the frame inside the canvas.
        val left = frame.left.coerceAtMost(width)
        val top = frame.top.coerceAtMost(height)
        val right = (frame.left + frame.width).coerceAtMost(width)
        val bottom = (frame.top + frame.height).coerceAtMost(height)
        val palette = frame.palette
        val missing = frame.missing
        for (y in top until bottom) {
            var src = (y - frame.top) * frame.width + (left - frame.left)
            var dst = y * width + left
            repeat(right - left) {
                val index = frame.indices[src].toInt() and 0xFF
                val present = missing == null || missing[src] == 0.toByte()
                if (present && index != frame.transparentIndex && index < palette.size) pixels[dst] = palette[index]
                src++
                dst++
            }
        }
        pendingDisposal = frame.disposal
        pendingLeft = left
        pendingTop = top
        pendingRight = right
        pendingBottom = bottom
        return pixels
    }
}
