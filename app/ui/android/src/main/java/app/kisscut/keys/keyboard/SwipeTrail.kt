package app.kisscut.keys.keyboard

import android.graphics.Canvas
import android.graphics.Paint

/**
 * The swipe trail (docs/KEYBOARD.md, Swipe typing): only the last [LIFE_MS] of the finger's movement shows,
 * fading and thinning toward its tail, and after the finger lifts the rest fades out in the same time. Points
 * live in a reused ring buffer, so drawing allocates nothing per frame.
 */
class SwipeTrail(private val capacity: Int = 256) {
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val times = LongArray(capacity)
    private var start = 0
    private var size = 0

    fun clear() {
        start = 0
        size = 0
    }

    /** A point the finger passed at [time] (uptime ms, as in MotionEvent). */
    fun add(x: Float, y: Float, time: Long) {
        val i = (start + size) % capacity
        xs[i] = x
        ys[i] = y
        times[i] = time
        if (size < capacity) size++ else start = (start + 1) % capacity
    }

    /** How many of the newest points are still young enough to draw at [now]. */
    fun visible(now: Long): Int {
        var n = 0
        while (n < size && now - times[(start + size - 1 - n) % capacity] < LIFE_MS) n++
        return n
    }

    /**
     * Draws what is still visible at [now] with [paint] (its colour and width are the head's). Returns true while
     * something is left to fade, so the caller keeps redrawing.
     */
    fun draw(canvas: Canvas, paint: Paint, now: Long): Boolean {
        val n = visible(now)
        if (n < 2) return n > 0
        val alpha = paint.alpha
        val width = paint.strokeWidth
        for (k in 0 until n - 1) {
            val a = (start + size - 1 - k) % capacity
            val b = (start + size - 2 - k) % capacity
            val life = 1f - (now - times[b]).toFloat() / LIFE_MS
            paint.alpha = (alpha * life).toInt().coerceIn(0, 255)
            paint.strokeWidth = width * (0.35f + 0.65f * life)
            canvas.drawLine(xs[a], ys[a], xs[b], ys[b], paint)
        }
        paint.alpha = alpha
        paint.strokeWidth = width
        return true
    }

    companion object {
        /** How long a stretch of the trail stays on screen. */
        const val LIFE_MS = 250L
    }
}
