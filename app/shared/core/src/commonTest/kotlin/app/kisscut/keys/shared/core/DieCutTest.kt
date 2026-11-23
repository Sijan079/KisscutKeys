package app.kisscut.keys.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DieCutTest {
    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()
    private val clear = 0x00000000

    private fun image(w: Int, h: Int, fill: (x: Int, y: Int) -> Int) =
        ArgbImage(w, h, IntArray(w * h) { fill(it % w, it / w) })

    private fun ArgbImage.at(x: Int, y: Int) = pixels[y * width + x]
    private fun alpha(argb: Int) = argb ushr 24

    @Test
    fun outputIsPaddedByRadiusPlusOneOnEverySide() {
        val out = DieCut.outline(image(10, 6) { _, _ -> red }, radius = 4)
        assertEquals(10 + 2 * 5, out.width)
        assertEquals(6 + 2 * 5, out.height)
        assertEquals(DieCut.padFor(4), 5)
    }

    @Test
    fun opaquePixelsKeepTheirColorAtTheShiftedPosition() {
        val src = image(3, 3) { x, y -> if (x == 1 && y == 1) red else clear }
        val out = DieCut.outline(src, radius = 3)
        val pad = DieCut.padFor(3)
        assertEquals(red, out.at(1 + pad, 1 + pad))
    }

    @Test
    fun cutLineReachesRadiusAndStopsAfterIt() {
        val src = image(1, 1) { _, _ -> red }
        val r = 5
        val out = DieCut.outline(src, radius = r)
        val c = DieCut.padFor(r) // centre of the single pixel
        // Inside the line: solid white.
        assertEquals(white, out.at(c + r - 1, c))
        assertEquals(white, out.at(c, c - (r - 1)))
        // Exactly at the radius: at least half covered (anti-aliased edge).
        assertTrue(alpha(out.at(c + r, c)) >= 127, "edge alpha ${alpha(out.at(c + r, c))}")
        // Past it: nothing. Corner of the padded image is far away.
        assertEquals(0, alpha(out.at(c + r + 1, c)))
        assertEquals(0, alpha(out.at(0, 0)))
    }

    @Test
    fun cutLineIsRoundNotSquare() {
        val r = 6
        val out = DieCut.outline(image(1, 1) { _, _ -> red }, radius = r)
        val c = DieCut.padFor(r)
        // Diagonal distance r*sqrt(2) ≈ 8.5 > r: the square's corner stays empty.
        assertEquals(0, alpha(out.at(c + r, c + r)))
    }

    @Test
    fun fullyTransparentInputStaysTransparent() {
        val out = DieCut.outline(image(8, 8) { _, _ -> clear }, radius = 3)
        assertTrue(out.pixels.all { alpha(it) == 0 })
    }

    @Test
    fun holesSmallerThanTheRadiusAreFilledWhite() {
        // A ring of red with a 1px transparent hole in the middle.
        val src = image(3, 3) { x, y -> if (x == 1 && y == 1) clear else red }
        val out = DieCut.outline(src, radius = 2)
        val pad = DieCut.padFor(2)
        assertEquals(white, out.at(1 + pad, 1 + pad))
    }

    @Test
    fun faintPixelsDoNotGrowALine() {
        // Alpha 40 is below the threshold: a soft glow should not get its own cut line.
        val faint = (40 shl 24) or 0xFF0000
        val out = DieCut.outline(image(1, 1) { _, _ -> faint }, radius = 4)
        val c = DieCut.padFor(4)
        assertEquals(0, alpha(out.at(c + 3, c)))
    }

    @Test
    fun semiTransparentEdgeIsCompositedOverTheLine() {
        // A half-transparent red pixel next to a solid one sits on the white line: result is opaque pink.
        val halfRed = (128 shl 24) or 0xFF0000
        val src = image(2, 1) { x, _ -> if (x == 0) red else halfRed }
        val out = DieCut.outline(src, radius = 3)
        val pad = DieCut.padFor(3)
        val p = out.at(1 + pad, pad)
        assertEquals(255, alpha(p))
        assertEquals(255, (p shr 16) and 0xFF)
        val green = (p shr 8) and 0xFF
        assertTrue(green in 120..135, "green $green")
    }

    @Test
    fun radiusForThumbnailIsAboutThreeDpAtTileSize() {
        assertEquals(12, DieCut.radiusFor(384))
    }
}
