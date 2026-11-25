package app.kisscut.keys.shared.core.words

import kotlin.math.sqrt

/**
 * Where the letter keys are on screen, in the same coordinates as the swipe path: [letters] a to z and the
 * centre of each key ([xs], [ys]), plus the width of one key.
 */
class KeyLayout(val letters: CharArray, val xs: FloatArray, val ys: FloatArray, val keyWidth: Float) {
    private val index = IntArray(26) { -1 }.also { idx -> letters.forEachIndexed { i, c -> idx[c - 'a'] = i } }

    fun has(c: Char): Boolean = c in 'a'..'z' && index[c - 'a'] >= 0
    fun x(c: Char): Float = xs[index[c - 'a']]
    fun y(c: Char): Float = ys[index[c - 'a']]

    /** Letters whose key centre is within [radius] of (x, y), nearest first. */
    fun near(x: Float, y: Float, radius: Float): List<Char> = letters.indices
        .map { it to dist(xs[it], ys[it], x, y) }
        .filter { it.second <= radius }
        .sortedBy { it.second }
        .map { letters[it.first] }
}

/**
 * Swipe typing (docs/KEYBOARD.md, Swipe typing): matches a finger path across the letter keys to words of the
 * bundled lists. Only words that start near where the finger went down and end near where it lifted are checked;
 * each one's ideal path (key centre to key centre, repeated letters once) is compared point by point with the
 * finger's, both resampled to the same number of points, and the closeness is weighed against how common the word
 * is. Pure; call it off the main thread.
 */
class SwipeMatcher(lists: List<WordList>) {
    private class Candidate(val word: Word, val letters: String)

    /** Candidates by first and last letter of the path: `first * 26 + last`. */
    private val byEnds: Array<MutableList<Candidate>> = Array(26 * 26) { ArrayList() }

    init {
        val seen = HashSet<String>()
        for (list in lists) for (w in list.words) {
            if (w.offensive || !seen.add(w.text)) continue
            val letters = squeeze(Speller.key(w.text))
            if (letters.length < 2 || letters.any { it !in 'a'..'z' }) continue
            byEnds[(letters.first() - 'a') * 26 + (letters.last() - 'a')] += Candidate(w, letters)
        }
    }

    /**
     * Up to [limit] words for the finger path [path] (x0, y0, x1, y1, ... in the [layout]'s coordinates), best
     * first, in the lists' own spelling. Empty when the path is too short to be a swipe.
     */
    fun match(rawPath: FloatArray, layout: KeyLayout, limit: Int = 4): List<String> {
        val w = layout.keyWidth
        val path = smooth(rawPath, w * MIN_STEP_KEYS)
        val count = path.size / 2
        if (count < 2) return emptyList()
        val pathLength = length(path, count)
        if (pathLength < w * MIN_PATH_KEYS) return emptyList()
        val user = FloatArray(SAMPLES * 2)
        resample(path, count, user)

        val starts = layout.near(path[0], path[1], w * END_RADIUS)
        val ends = layout.near(path[(count - 1) * 2], path[(count - 1) * 2 + 1], w * END_RADIUS)
        val ideal = FloatArray(MAX_LETTERS * 2)
        val idealSampled = FloatArray(SAMPLES * 2)
        val grid = FloatArray(SAMPLES * SAMPLES)
        val scored = ArrayList<Pair<String, Double>>()
        for (s in starts) for (e in ends) {
            for (c in byEnds[(s - 'a') * 26 + (e - 'a')]) {
                val n = c.letters.length
                if (n > MAX_LETTERS || c.letters.any { !layout.has(it) }) continue
                for (i in 0 until n) {
                    ideal[i * 2] = layout.x(c.letters[i])
                    ideal[i * 2 + 1] = layout.y(c.letters[i])
                }
                // A word whose ideal path is far shorter or longer than the finger's can't be it.
                val idealLength = length(ideal, n)
                if (idealLength > pathLength * 2.2f + w || idealLength < pathLength * 0.45f - w) continue
                // Cheap first: every letter's key must lie near the finger's path, or the word can't be it.
                if (!passesNear(c.letters, layout, user, w * PASS_KEYS)) continue
                resample(ideal, n, idealSampled)
                val meanKeys = warpedMean(user, idealSampled, grid, w * MAX_MEAN_KEYS.toFloat()) / w
                if (meanKeys > MAX_MEAN_KEYS) continue
                // People aim where they start and lift: a word whose first or last key is off those points loses more.
                val ends = dist(path[0], path[1], ideal[0], ideal[1]) +
                    dist(path[(count - 1) * 2], path[(count - 1) * 2 + 1], ideal[(n - 1) * 2], ideal[(n - 1) * 2 + 1])
                scored += c.word.text to (c.word.frequency - SHAPE_WEIGHT * meanKeys - END_WEIGHT * ends / w)
            }
        }
        return scored.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .map { it.first }
            .distinct()
            .take(limit)
    }

    companion object {
        /** Points each path is resampled to before comparing. */
        const val SAMPLES = 32
        private const val MAX_LETTERS = 40

        /** How far (in keys) the first and last letters may be from where the finger went down and lifted. */
        private const val END_RADIUS = 1.1f

        /** A path shorter than this many key widths is a tap, not a swipe. */
        private const val MIN_PATH_KEYS = 0.6f

        /** How far (in keys) from the finger's path every letter's key must be. */
        private const val PASS_KEYS = 1.3f

        /** How far out of step (in samples) two paths may be compared. */
        private const val BAND = 6

        /** Mean distance (in keys) past which a word is not considered at all. */
        private const val MAX_MEAN_KEYS = 2.0

        /** Score points lost per key between where the finger went down or lifted and the word's first or last key. */
        private const val END_WEIGHT = 70.0

        /** Score points lost per key of mean distance; frequencies run 0 to 255. */
        private const val SHAPE_WEIGHT = 220.0

        /** Points closer than this (in keys) to the last kept one are finger jitter, not shape. */
        private const val MIN_STEP_KEYS = 0.25f

        /**
         * Drops points within [minStep] of the last kept one (always keeping the last point), so jitter doesn't
         * stretch the path: resampling spaces points by length, and a wobbly path is longer than its shape.
         */
        fun smooth(path: FloatArray, minStep: Float): FloatArray {
            val count = path.size / 2
            if (count < 3) return path
            val out = FloatArray(path.size)
            out[0] = path[0]
            out[1] = path[1]
            var kept = 1
            for (i in 1 until count - 1) {
                if (dist(out[kept * 2 - 2], out[kept * 2 - 1], path[i * 2], path[i * 2 + 1]) >= minStep) {
                    out[kept * 2] = path[i * 2]
                    out[kept * 2 + 1] = path[i * 2 + 1]
                    kept++
                }
            }
            out[kept * 2] = path[(count - 1) * 2]
            out[kept * 2 + 1] = path[(count - 1) * 2 + 1]
            kept++
            return out.copyOf(kept * 2)
        }

        /**
         * Distance between two paths ([SAMPLES] points each) that keeps their order but lets one run ahead of the
         * other (dynamic time warping), so finger jitter doesn't throw the comparison off: the cheapest way to walk
         * both from start to end together, per step. [grid] is scratch of [SAMPLES] squared.
         */
        private fun warpedMean(a: FloatArray, b: FloatArray, grid: FloatArray, giveUpMean: Float): Double {
            val n = SAMPLES
            val far = Float.MAX_VALUE / 4
            // Only points roughly in step are compared (a band around the diagonal), and a word is dropped as soon
            // as even its best partial walk is too far on average.
            val giveUp = giveUpMean * (2 * n - 1)
            for (i in 0 until n) {
                var rowMin = far
                for (j in 0 until n) {
                    if (j < i - BAND || j > i + BAND) {
                        grid[i * n + j] = far
                        continue
                    }
                    val here = dist(a[i * 2], a[i * 2 + 1], b[j * 2], b[j * 2 + 1])
                    val before = when {
                        i == 0 && j == 0 -> 0f
                        i == 0 -> grid[j - 1]
                        j == 0 -> grid[(i - 1) * n]
                        else -> minOf(grid[(i - 1) * n + j], grid[i * n + j - 1], grid[(i - 1) * n + j - 1])
                    }
                    val v = before + here
                    grid[i * n + j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > giveUp) return Double.MAX_VALUE
            }
            return grid[n * n - 1].toDouble() / (2 * n - 1)
        }

        /** Each letter's key is within [radius] of some point of the (resampled) finger path. */
        private fun passesNear(letters: String, layout: KeyLayout, user: FloatArray, radius: Float): Boolean {
            val r2 = radius * radius
            for (c in letters) {
                val x = layout.x(c)
                val y = layout.y(c)
                var near = false
                for (i in 0 until SAMPLES) {
                    val dx = user[i * 2] - x
                    val dy = user[i * 2 + 1] - y
                    if (dx * dx + dy * dy <= r2) {
                        near = true
                        break
                    }
                }
                if (!near) return false
            }
            return true
        }

        /** "hello" traces h-e-l-o: a repeated letter is one key. */
        fun squeeze(key: String): String = buildString(key.length) {
            for (c in key) if (isEmpty() || last() != c) append(c)
        }

        private fun length(p: FloatArray, count: Int): Float {
            var total = 0f
            for (i in 1 until count) total += dist(p[i * 2 - 2], p[i * 2 - 1], p[i * 2], p[i * 2 + 1])
            return total
        }

        /** Resamples the polyline [p] ([count] points) to [SAMPLES] points evenly spaced along it, into [out]. */
        fun resample(p: FloatArray, count: Int, out: FloatArray) {
            val total = length(p, count)
            if (count == 1 || total == 0f) {
                for (i in 0 until SAMPLES) {
                    out[i * 2] = p[0]
                    out[i * 2 + 1] = p[1]
                }
                return
            }
            val step = total / (SAMPLES - 1)
            var seg = 1
            var segStart = 0f
            var segLen = dist(p[0], p[1], p[2], p[3])
            for (i in 0 until SAMPLES) {
                val target = minOf(i * step, total)
                while (seg < count - 1 && segStart + segLen < target) {
                    segStart += segLen
                    seg++
                    segLen = dist(p[seg * 2 - 2], p[seg * 2 - 1], p[seg * 2], p[seg * 2 + 1])
                }
                val t = if (segLen == 0f) 0f else ((target - segStart) / segLen).coerceIn(0f, 1f)
                out[i * 2] = p[seg * 2 - 2] + (p[seg * 2] - p[seg * 2 - 2]) * t
                out[i * 2 + 1] = p[seg * 2 - 1] + (p[seg * 2 + 1] - p[seg * 2 - 1]) * t
            }
        }
    }
}

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return sqrt(dx * dx + dy * dy)
}
