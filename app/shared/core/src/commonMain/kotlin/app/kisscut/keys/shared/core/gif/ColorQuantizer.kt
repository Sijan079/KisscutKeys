package app.kisscut.keys.shared.core.gif

/**
 * Picks at most [maxColors] colors for a set of opaque RGB colors (weighted median cut), then maps
 * each color to its nearest palette entry. When the input has no more than [maxColors] distinct
 * colors the palette is exactly those colors, so nothing is lost. Deterministic: same input, same
 * palette; no dithering, so unchanged areas of an animation never shimmer.
 */
internal class ColorQuantizer(private val maxColors: Int) {
    class Result(
        /** Opaque ARGB. */
        val palette: IntArray,
        /** Palette index for each distinct color passed in (keys are RGB, alpha ignored). */
        val lookup: Map<Int, Int>,
    )

    /** [counts]: RGB color to how many pixels have it. */
    fun quantize(counts: Map<Int, Int>): Result {
        val colors = counts.keys.sorted().toIntArray()
        if (colors.size <= maxColors) {
            val lookup = HashMap<Int, Int>(colors.size * 2)
            colors.forEachIndexed { i, c -> lookup[c] = i }
            return Result(IntArray(colors.size) { colors[it] or OPAQUE }, lookup)
        }
        val weights = IntArray(colors.size) { counts.getValue(colors[it]) }
        val boxes = ArrayList<Box>()
        boxes += Box(colors, weights, 0, colors.size)
        while (boxes.size < maxColors) {
            // Split the box with the most squared error; ties go to the earliest box.
            var best = -1
            for (i in boxes.indices) {
                if (boxes[i].size > 1 && (best < 0 || boxes[i].error > boxes[best].error)) best = i
            }
            if (best < 0 || boxes[best].error == 0.0) break
            val (a, b) = boxes[best].split()
            boxes[best] = a
            boxes.add(b)
        }
        val palette = IntArray(boxes.size) { boxes[it].mean or OPAQUE }
        val lookup = HashMap<Int, Int>(colors.size * 2)
        for (c in colors) lookup[c] = nearest(palette, c)
        return Result(palette, lookup)
    }

    /** A range [from, to) of [colors] (and their [weights]), kept sorted along the axis it was cut on. */
    private class Box(val colors: IntArray, val weights: IntArray, val from: Int, val to: Int) {
        val size get() = to - from
        val mean: Int
        val error: Double
        private val axis: Int

        init {
            var w = 0.0
            val sum = DoubleArray(3)
            val sumSq = DoubleArray(3)
            for (i in from until to) {
                val weight = weights[i].toDouble()
                w += weight
                for (ch in 0..2) {
                    val v = channel(colors[i], ch).toDouble()
                    sum[ch] += weight * v
                    sumSq[ch] += weight * v * v
                }
            }
            val variance = DoubleArray(3) { sumSq[it] - sum[it] * sum[it] / w }
            error = variance.sum()
            axis = (0..2).maxBy { variance[it] }
            val r = (sum[0] / w).roundToChannel()
            val g = (sum[1] / w).roundToChannel()
            val b = (sum[2] / w).roundToChannel()
            mean = (r shl 16) or (g shl 8) or b
        }

        /** Cuts at the weighted median along the widest-spread channel. */
        fun split(): Pair<Box, Box> {
            sortRange(axis)
            var total = 0L
            for (i in from until to) total += weights[i]
            var acc = 0L
            var cut = from + 1
            for (i in from until to - 1) {
                acc += weights[i]
                cut = i + 1
                if (acc * 2 >= total) break
            }
            return Box(colors, weights, from, cut) to Box(colors, weights, cut, to)
        }

        /** Sorts the range by one channel, then by the whole color, so the order is always the same. */
        private fun sortRange(ch: Int) {
            val order = (from until to).sortedWith(compareBy({ channel(colors[it], ch) }, { colors[it] }))
            val c = IntArray(size) { colors[order[it]] }
            val w = IntArray(size) { weights[order[it]] }
            c.copyInto(colors, from)
            w.copyInto(weights, from)
        }
    }

    private companion object {
        const val OPAQUE = 0xFF shl 24

        fun channel(rgb: Int, ch: Int) = (rgb shr (16 - 8 * ch)) and 0xFF

        fun Double.roundToChannel() = (this + 0.5).toInt().coerceIn(0, 255)

        /** Nearest palette entry by squared RGB distance; ties go to the lower index. */
        fun nearest(palette: IntArray, rgb: Int): Int {
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (i in palette.indices) {
                val p = palette[i]
                val dr = ((p shr 16) and 0xFF) - ((rgb shr 16) and 0xFF)
                val dg = ((p shr 8) and 0xFF) - ((rgb shr 8) and 0xFF)
                val db = (p and 0xFF) - (rgb and 0xFF)
                val d = dr * dr + dg * dg + db * db
                if (d < bestDist) {
                    best = i
                    bestDist = d
                }
            }
            return best
        }
    }
}
