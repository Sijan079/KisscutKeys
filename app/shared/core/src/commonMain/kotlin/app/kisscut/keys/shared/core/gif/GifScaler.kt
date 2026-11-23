package app.kisscut.keys.shared.core.gif

import kotlin.math.roundToInt

/**
 * Area-average downscaling of a canvas whose pixels are fully opaque or fully transparent (0).
 * Every output pixel averages the exact source area it covers (fractional edges weighted), in
 * premultiplied form so transparent pixels add no dark halo. The result is 1-bit again: a pixel is
 * opaque when at least half its area is, with the average color of its opaque part.
 */
class GifScaler(val srcWidth: Int, val srcHeight: Int, val outWidth: Int, val outHeight: Int) {
    private val xTaps = taps(srcWidth, outWidth)
    private val yTaps = taps(srcHeight, outHeight)

    // Running sums for one output row: alpha weight and premultiplied R, G, B, per output column.
    private val sumA = FloatArray(outWidth)
    private val sumR = FloatArray(outWidth)
    private val sumG = FloatArray(outWidth)
    private val sumB = FloatArray(outWidth)

    init {
        require(outWidth in 1..srcWidth && outHeight in 1..srcHeight) { "only scales down" }
    }

    /** Scales [src] (srcWidth x srcHeight ARGB) into [out] (outWidth x outHeight). */
    fun scale(src: IntArray, out: IntArray = IntArray(outWidth * outHeight)): IntArray {
        for (oy in 0 until outHeight) {
            sumA.fill(0f)
            sumR.fill(0f)
            sumG.fill(0f)
            sumB.fill(0f)
            val ys = yTaps[oy]
            for (t in ys.indices step 2) {
                val row = ys[t].toInt() * srcWidth
                val wy = ys[t + 1]
                for (ox in 0 until outWidth) {
                    val xs = xTaps[ox]
                    for (k in xs.indices step 2) {
                        val p = src[row + xs[k].toInt()]
                        if (p ushr 24 == 0) continue
                        val w = wy * xs[k + 1]
                        sumA[ox] += w
                        sumR[ox] += w * ((p shr 16) and 0xFF)
                        sumG[ox] += w * ((p shr 8) and 0xFF)
                        sumB[ox] += w * (p and 0xFF)
                    }
                }
            }
            val base = oy * outWidth
            for (ox in 0 until outWidth) {
                val a = sumA[ox]
                out[base + ox] = if (a < 0.5f) {
                    0
                } else {
                    val r = (sumR[ox] / a).roundToInt().coerceIn(0, 255)
                    val g = (sumG[ox] / a).roundToInt().coerceIn(0, 255)
                    val b = (sumB[ox] / a).roundToInt().coerceIn(0, 255)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return out
    }

    companion object {
        /** Size with the longest side at most [maxSide], aspect kept, each side at least 1. */
        fun fit(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
            val longest = maxOf(width, height)
            if (longest <= maxSide) return width to height
            val w = maxOf(1, (width.toLong() * maxSide / longest.toDouble()).roundToInt())
            val h = maxOf(1, (height.toLong() * maxSide / longest.toDouble()).roundToInt())
            return w to h
        }

        /**
         * For each output index, pairs of (source index, weight) packed as floats; weights are the
         * covered fraction of each source pixel and sum to 1.
         */
        private fun taps(src: Int, out: Int): Array<FloatArray> {
            val scale = src.toDouble() / out
            return Array(out) { o ->
                val start = o * scale
                val end = (o + 1) * scale
                val first = start.toInt()
                val last = minOf(src - 1, kotlin.math.ceil(end).toInt() - 1)
                val taps = FloatArray(2 * (last - first + 1))
                for (s in first..last) {
                    val cover = minOf(end, s + 1.0) - maxOf(start, s.toDouble())
                    taps[2 * (s - first)] = s.toFloat()
                    taps[2 * (s - first) + 1] = (cover / scale).toFloat()
                }
                taps
            }
        }
    }
}
