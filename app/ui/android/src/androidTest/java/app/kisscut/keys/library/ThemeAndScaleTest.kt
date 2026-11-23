package app.kisscut.keys.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kisscut.keys.TestLibrary
import app.kisscut.keys.common.PartTags
import app.kisscut.keys.common.TileTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.test.assertTrue

/** Key screens in dark and light (TESTING.md, Theme) and at font scale 2.0 (no clipped nav). */
@RunWith(AndroidJUnit4::class)
class ThemeAndScaleTest {
    @get:Rule
    val compose = createComposeRule()
    private val lib = TestLibrary()

    @After
    fun close() = lib.close()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun near(a: Color, b: Color) =
        abs(a.red - b.red) < 0.02f && abs(a.green - b.green) < 0.02f && abs(a.blue - b.blue) < 0.02f

    private fun backgroundIs(dark: Boolean, expected: Color) {
        val s = runBlocking { lib.face(1) }
        compose.setContent { lib.App(dark = dark) }
        compose.waitUntil(10_000) { exists(TileTags.sticker(s.id)) }
        val px = compose.onRoot().captureToImage().toPixelMap()
        // Just below the status bar area at the right edge: plain background.
        val sample = px[px.width - 4, px.height / 2]
        assertTrue(near(sample, expected), "background $sample, expected $expected")
    }

    @Test
    fun libraryRendersOnInkInDark() = backgroundIs(dark = true, Color(0xFF0F0E15))

    @Test
    fun libraryRendersOnPaperInLight() = backgroundIs(dark = false, Color(0xFFF7F6FB))

    @Test
    fun navKeepsEveryButtonOnScreenAtDoubleFontSize() {
        runBlocking { lib.face(1) }
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) { lib.App() }
        }
        compose.waitUntil(10_000) { exists(PartTags.NAV_SETTINGS) }
        // On the Collections tab the active label is longest.
        compose.onNode(hasTestTag(PartTags.NAV_COLLECTIONS)).performClick()
        compose.waitForIdle()
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
        val px = compose.density.run { 1.dp.toPx() }
        // The bar sits 20dp from each screen edge; every button must keep its full 48dp target inside it.
        val barRight = rootWidth - 20 * px
        for (tag in listOf(PartTags.NAV_LIBRARY, PartTags.NAV_COLLECTIONS, PartTags.NAV_ADD, PartTags.NAV_SETTINGS)) {
            // Unclipped position + size: boundsInRoot is clipped by the bar and would hide an overflow.
            val node = compose.onNode(hasTestTag(tag)).fetchSemanticsNode()
            val left = node.positionInRoot.x
            val right = left + node.size.width
            assertTrue(node.size.width >= 48 * px - 1, "$tag squeezed to ${node.size.width / px}dp")
            assertTrue(left >= 20 * px - 1 && right <= barRight + 1, "$tag spans $left..$right, bar ends at $barRight")
        }
    }
}
