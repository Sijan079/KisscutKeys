package app.kisscut.keys.keyboard

import org.junit.Test
import kotlin.test.assertEquals

class SwipeTrailTest {
    @Test
    fun onlyTheLastQuarterSecondShowsAndTheRestFadesAfterLift() {
        val trail = SwipeTrail(capacity = 8)
        for (i in 0 until 6) trail.add(i * 10f, 0f, time = 1_000L + i * 100)
        // At 1,550 ms only points from the last 250 ms are left: the ones at 1,400 and 1,500.
        assertEquals(2, trail.visible(now = 1_550))
        // After the finger lifts nothing new comes; everything is gone 250 ms after the last point.
        assertEquals(1, trail.visible(now = 1_700))
        assertEquals(0, trail.visible(now = 1_750))
    }

    @Test
    fun aLongSwipeKeepsOnlyTheNewestPoints() {
        val trail = SwipeTrail(capacity = 4)
        for (i in 0 until 10) trail.add(i.toFloat(), 0f, time = 1_000L + i)
        assertEquals(4, trail.visible(now = 1_010))
        trail.clear()
        assertEquals(0, trail.visible(now = 1_010))
    }
}
