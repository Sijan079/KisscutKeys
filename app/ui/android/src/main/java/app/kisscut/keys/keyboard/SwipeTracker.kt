package app.kisscut.keys.keyboard

import app.kisscut.keys.shared.core.words.KeyLayout
import kotlin.math.hypot

/**
 * One finger's swipe on the letters (docs/KEYBOARD.md, Swipe typing): armed when it goes down on a letter, active
 * once it has left that key and travelled [START_KEYS] of a key width. Collects the path in a reused buffer, so a
 * swipe allocates nothing per move.
 */
class SwipeTracker {
    var armed = false
        private set
    var active = false
        private set
    var startX = 0f
        private set
    var startY = 0f
        private set
    var layout: KeyLayout? = null
        private set
    private var startKey: Char = ' '
    private var points = FloatArray(512)
    private var count = 0

    fun arm(x: Float, y: Float, key: Char, keys: KeyLayout) {
        armed = true
        active = false
        startX = x
        startY = y
        startKey = key
        layout = keys
        count = 0
    }

    /** The finger is far enough from where it went down, and nearer another key than its own, to be a swipe. */
    fun leftStartKey(x: Float, y: Float): Boolean {
        val keys = layout ?: return false
        if (hypot(x - startX, y - startY) < keys.keyWidth * START_KEYS) return false
        return keys.near(x, y, Float.MAX_VALUE).firstOrNull() != startKey
    }

    fun start(x: Float, y: Float) {
        active = true
        count = 0
        add(startX, startY)
        add(x, y)
    }

    fun add(x: Float, y: Float) {
        if (count * 2 + 2 > points.size) points = points.copyOf(points.size * 2)
        points[count * 2] = x
        points[count * 2 + 1] = y
        count++
    }

    fun path(): FloatArray = points.copyOf(count * 2)

    fun reset() {
        armed = false
        active = false
        layout = null
        count = 0
    }

    private companion object {
        /** How far, in key widths, the finger must travel before a press becomes a swipe. */
        const val START_KEYS = 0.8f
    }
}
