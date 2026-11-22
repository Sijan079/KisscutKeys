package app.kisscut.keys.shared.core

import kotlin.math.sqrt

/** Non-premultiplied ARGB pixels, row by row. */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "bad image ${width}x$height" }
    }
}

/**
 * The Kisscut look's cut line (docs/UI_SPEC.md): a white line around the shape of a transparent sticker.
 * Baked once into the thumbnail at import; never computed while scrolling. The soft shadow is drawn by
 * the UI, because its strength differs between dark and light themes.
 */
object DieCut {
    /** Pixels at or above this alpha count as part of the shape. */
    const val ALPHA_THRESHOLD = 128

    /** About 3dp when a [thumbSide]-px thumbnail fills a ~90dp sticker tile. */
    fun radiusFor(thumbSide: Int): Int = (thumbSide / 32).coerceAtLeast(1)

    /** Transparent margin added on each side so the line is never clipped. */
    fun padFor(radius: Int): Int = radius + 1

    /**
     * Returns [src] padded by [padFor] on every side, with a [color] line [radius] px wide around every
     * solid region (anti-aliased edge) and [src] drawn on top. Holes narrower than the line fill in.
     */
    fun outline(src: ArgbImage, radius: Int, color: Int = 0xFFFFFFFF.toInt()): ArgbImage {
        require(radius > 0) { "radius must be positive" }
        val pad = padFor(radius)
        val w = src.width + 2 * pad
        val h = src.height + 2 * pad
        val cover = FloatArray(w * h)

        val sw = src.width
        val sh = src.height
        fun solid(x: Int, y: Int) = x in 0 until sw && y in 0 until sh &&
            (src.pixels[y * sw + x] ushr 24) >= ALPHA_THRESHOLD

        val disk = diskOffsets(radius)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                if (!solid(x, y)) continue
                val ox = x + pad
                val oy = y + pad
                cover[oy * w + ox] = 1f
                val boundary = !solid(x - 1, y) || !solid(x + 1, y) || !solid(x, y - 1) || !solid(x, y + 1)
                if (!boundary) continue
                for (o in disk) {
                    val i = (oy + o.dy) * w + (ox + o.dx)
                    if (o.cover > cover[i]) cover[i] = o.cover
                }
            }
        }

        val lineA = (color ushr 24) / 255f
        val lineRgb = color and 0xFFFFFF
        val out = IntArray(w * h) { i ->
            val x = i % w - pad
            val y = i / w - pad
            val s = if (x in 0 until sw && y in 0 until sh) src.pixels[y * sw + x] else 0
            srcOver(s, lineRgb, lineA * cover[i])
        }
        return ArgbImage(w, h, out)
    }

    private class Offset(val dx: Int, val dy: Int, val cover: Float)

    private fun diskOffsets(radius: Int): List<Offset> {
        val list = mutableListOf<Offset>()
        val reach = radius + 1
        for (dy in -reach..reach) {
            for (dx in -reach..reach) {
                val c = (radius + 0.5f - sqrt((dx * dx + dy * dy).toFloat())).coerceIn(0f, 1f)
                if (c > 0f) list += Offset(dx, dy, c)
            }
        }
        return list
    }

    /** [top] (non-premultiplied ARGB) over a [bottomRgb] layer of alpha [bottomA]. */
    private fun srcOver(top: Int, bottomRgb: Int, bottomA: Float): Int {
        val ta = (top ushr 24) / 255f
        val outA = ta + bottomA * (1f - ta)
        if (outA <= 0f) return 0
        fun channel(shift: Int): Int {
            val t = (top shr shift) and 0xFF
            val b = (bottomRgb shr shift) and 0xFF
            return ((t * ta + b * bottomA * (1f - ta)) / outA + 0.5f).toInt().coerceIn(0, 255)
        }
        val a = (outA * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
