package com.example.stickers.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickers.R

// ---------------------------------------------------------------------------------------------
// Raw tokens from docs/UI_SPEC.md ("Die-cut"). The only place hex values may appear.
// Note: "Ink" names two different colors in the spec: the dark background (#0F0E15) and the
// light-mode text (#14131C). They are kept apart here as DarkInk and LightInk.
// ---------------------------------------------------------------------------------------------

internal object DieCutDark {
    val Ink = Color(0xFF0F0E15)
    val Surface = Color(0xFF181722)
    val Raised = Color(0xFF211F2E)
    val Line = Color(0xFF2D2B3D)
    val Text = Color(0xFFF5F4FB)
    val Muted = Color(0xFFA9A7BC)
    val Danger = Color(0xFFFF8A7A)
}

internal object DieCutLight {
    val Paper = Color(0xFFF7F6FB)
    val Surface = Color(0xFFFFFFFF)
    val Raised = Color(0xFFECEAF4)
    val Line = Color(0xFFDDDAEA)
    val Ink = Color(0xFF14131C)
    val Muted = Color(0xFF5E5C72)
    val Olive = Color(0xFF4D6B00)
    val VioletIcon = Color(0xFF5B4BD6)
    val Danger = Color(0xFFB3261E)
}

internal object DieCutShared {
    val Lime = Color(0xFFC8F23D)
    val Violet = Color(0xFF9B8CFF)
    val StickerInk = Color(0xFF14131C)
    val White = Color(0xFFFFFFFF)
}

// ---------------------------------------------------------------------------------------------
// Extras: tokens Material 3 has no role for. Read via StickersTheme.colors.
// ---------------------------------------------------------------------------------------------

@Immutable
data class StickerColors(
    val raised: Color,
    val line: Color,
    val muted: Color,
    /** Lime fill for buttons and the active pill. */
    val lime: Color,
    /** Text or icon on a lime fill. */
    val onLime: Color,
    /** Lime used as text or icon: Lime in dark, Olive in light. */
    val accentText: Color,
    /** Selection ring and check badge: Lime in dark, Olive in light. */
    val selection: Color,
    /** Check mark drawn on the selection badge. */
    val onSelection: Color,
    val violet: Color,
    /** Violet icon tiles (Settings): #9B8CFF dark, #5B4BD6 light. */
    val violetIcon: Color,
    val danger: Color,
    /** Sticker eyes and mouth. */
    val stickerInk: Color,
    /** Sticker cut line. */
    val cutLine: Color,
    /** Shadow alpha under stickers: about 40% dark, 25% light. */
    val stickerShadowAlpha: Float,
)

internal val DarkStickerColors = StickerColors(
    raised = DieCutDark.Raised,
    line = DieCutDark.Line,
    muted = DieCutDark.Muted,
    lime = DieCutShared.Lime,
    onLime = DieCutDark.Ink,
    accentText = DieCutShared.Lime,
    selection = DieCutShared.Lime,
    onSelection = DieCutDark.Ink,
    violet = DieCutShared.Violet,
    violetIcon = DieCutShared.Violet,
    danger = DieCutDark.Danger,
    stickerInk = DieCutShared.StickerInk,
    cutLine = DieCutShared.White,
    stickerShadowAlpha = 0.40f,
)

internal val LightStickerColors = StickerColors(
    raised = DieCutLight.Raised,
    line = DieCutLight.Line,
    muted = DieCutLight.Muted,
    lime = DieCutShared.Lime,
    onLime = DieCutLight.Ink,
    accentText = DieCutLight.Olive,
    selection = DieCutLight.Olive,
    onSelection = DieCutShared.White,
    violet = DieCutShared.Violet,
    violetIcon = DieCutLight.VioletIcon,
    danger = DieCutLight.Danger,
    stickerInk = DieCutShared.StickerInk,
    cutLine = DieCutShared.White,
    stickerShadowAlpha = 0.25f,
)

// ---------------------------------------------------------------------------------------------
// Material 3 color schemes (one scheme swapped, not two sets of screens).
// ---------------------------------------------------------------------------------------------

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = DieCutShared.Lime,
    onPrimary = DieCutDark.Ink,
    secondary = DieCutShared.Violet,
    onSecondary = DieCutDark.Ink,
    background = DieCutDark.Ink,
    onBackground = DieCutDark.Text,
    surface = DieCutDark.Surface,
    onSurface = DieCutDark.Text,
    surfaceVariant = DieCutDark.Raised,
    onSurfaceVariant = DieCutDark.Muted,
    surfaceContainer = DieCutDark.Raised,
    // Selected chip and selected segment use the inverted fill.
    inverseSurface = DieCutDark.Text,
    inverseOnSurface = DieCutDark.Ink,
    outline = DieCutDark.Line,
    outlineVariant = DieCutDark.Line,
    error = DieCutDark.Danger,
    onError = DieCutDark.Ink,
)

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = DieCutShared.Lime,
    onPrimary = DieCutLight.Ink,
    secondary = DieCutShared.Violet,
    onSecondary = DieCutLight.Ink,
    background = DieCutLight.Paper,
    onBackground = DieCutLight.Ink,
    surface = DieCutLight.Surface,
    onSurface = DieCutLight.Ink,
    surfaceVariant = DieCutLight.Raised,
    onSurfaceVariant = DieCutLight.Muted,
    surfaceContainer = DieCutLight.Raised,
    inverseSurface = DieCutLight.Ink,
    inverseOnSurface = DieCutDark.Text,
    outline = DieCutLight.Line,
    outlineVariant = DieCutLight.Line,
    error = DieCutLight.Danger,
    onError = DieCutShared.White,
)

fun stickerColorScheme(dark: Boolean): ColorScheme = if (dark) DarkScheme else LightScheme

fun stickerColors(dark: Boolean): StickerColors = if (dark) DarkStickerColors else LightStickerColors

// ---------------------------------------------------------------------------------------------
// Type: bundled variable fonts (offline), Bricolage Grotesque for titles and buttons,
// Figtree for body and labels.
// ---------------------------------------------------------------------------------------------

private fun bricolage(weight: Int) = Font(
    resId = R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun figtree(weight: Int) = Font(
    resId = R.font.figtree,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Bricolage = FontFamily(bricolage(700), bricolage(800))
val Figtree = FontFamily(figtree(500), figtree(600), figtree(700))

/** Named styles from UI_SPEC. Also mapped into Material 3 [Typography] below. */
object StickerType {
    val Title = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight(800),
        fontSize = 36.sp,
        letterSpacing = (-1).sp,
    )
    val Heading = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(700), fontSize = 18.sp)
    val Body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 15.sp)
    val Label = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(700), fontSize = 12.sp)
    val Button = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(800), fontSize = 15.sp)
}

internal val StickerTypography = Typography(
    headlineLarge = StickerType.Title,
    titleLarge = StickerType.Heading,
    bodyLarge = StickerType.Body,
    bodyMedium = StickerType.Body,
    labelLarge = StickerType.Button,
    labelMedium = StickerType.Label,
    labelSmall = StickerType.Label,
)

// ---------------------------------------------------------------------------------------------
// Shape
// ---------------------------------------------------------------------------------------------

object StickerShapes {
    val Tile = RoundedCornerShape(20.dp)
    val Card = RoundedCornerShape(26.dp)
    val Sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

object StickerSizes {
    val PrimaryButtonHeight = 44.dp
    val ChipHeight = 36.dp
    val MinTouchTarget = 48.dp
}

// ---------------------------------------------------------------------------------------------
// Theme entry point
// ---------------------------------------------------------------------------------------------

private val LocalStickerColors = staticCompositionLocalOf { DarkStickerColors }

/** Follows the system setting by default. A forced theme (Settings) is added in M5. */
@Composable
fun StickersTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStickerColors provides stickerColors(dark)) {
        MaterialTheme(
            colorScheme = stickerColorScheme(dark),
            typography = StickerTypography,
            content = content,
        )
    }
}

object StickersTheme {
    val colors: StickerColors
        @Composable @ReadOnlyComposable
        get() = LocalStickerColors.current
}
