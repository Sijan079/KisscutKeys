package app.kisscut.keys.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals

/** Checks Theme.kt against the token tables in docs/UI_SPEC.md. */
class ThemeTokensTest {

    @Test
    fun darkSchemeUsesDieCutDarkTokens() {
        val s = stickerColorScheme(dark = true)
        assertEquals(Color(0xFF0F0E15), s.background, "background = Ink")
        assertEquals(Color(0xFF181722), s.surface, "surface = Surface")
        assertEquals(Color(0xFFF5F4FB), s.onBackground, "text = Text")
        assertEquals(Color(0xFFA9A7BC), s.onSurfaceVariant, "secondary text = Muted")
        assertEquals(Color(0xFF2D2B3D), s.outline, "borders = Line")
        assertEquals(Color(0xFFC8F23D), s.primary, "primary = Lime")
        assertEquals(Color(0xFF0F0E15), s.onPrimary, "text on lime = Ink")
    }

    @Test
    fun lightSchemeUsesDieCutLightTokens() {
        val s = stickerColorScheme(dark = false)
        assertEquals(Color(0xFFF7F6FB), s.background, "background = Paper")
        assertEquals(Color(0xFFFFFFFF), s.surface, "surface = Surface")
        assertEquals(Color(0xFF14131C), s.onBackground, "text = Ink")
        assertEquals(Color(0xFF5E5C72), s.onSurfaceVariant, "secondary text = Muted")
        assertEquals(Color(0xFFDDDAEA), s.outline, "borders = Line")
        assertEquals(Color(0xFFC8F23D), s.primary, "primary = Lime")
        assertEquals(Color(0xFF14131C), s.onPrimary, "text on lime = Ink")
    }

    @Test
    fun extrasSwapLimeTextForOliveInLight() {
        val dark = stickerColors(dark = true)
        val light = stickerColors(dark = false)
        assertEquals(Color(0xFFC8F23D), dark.accentText)
        assertEquals(Color(0xFF4D6B00), light.accentText)
        assertEquals(Color(0xFF4D6B00), light.selection)
        assertEquals(Color(0xFF5B4BD6), light.violetIcon)
        assertEquals(Color(0xFFFF8A7A), dark.danger)
        assertEquals(Color(0xFFB3261E), light.danger)
        assertEquals(0.40f, dark.stickerShadowAlpha)
        assertEquals(0.25f, light.stickerShadowAlpha)
    }

    @Test
    fun tileTintsComeInMatchingDarkAndLightSets() {
        val dark = stickerColors(dark = true)
        val light = stickerColors(dark = false)
        assertEquals(8, dark.tileTints.size)
        assertEquals(dark.tileTints.size, light.tileTints.size)
        assertEquals(Color(0xFF24203F), dark.tileTints[1], "violet tint, dark")
        assertEquals(Color(0xFFDCD7FA), light.tileTints[1], "violet tint, light")
        assertEquals(Color(0xFF4A4860), dark.dashed)
        assertEquals(Color(0xFFC4C1D6), light.dashed)
    }
}
