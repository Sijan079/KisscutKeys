package com.example.stickers.keyboard

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import com.example.stickers.ui.theme.stickerColorScheme
import com.example.stickers.ui.theme.stickerColors

/**
 * The die-cut tokens from Theme.kt as ARGB ints for the View-based panel. No hex values here:
 * the keyboard reads the same tokens as the app (docs/KEYBOARD.md, States to handle).
 */
data class KeyboardPalette(
    val dark: Boolean,
    val panel: Int,
    val raised: Int,
    val line: Int,
    val text: Int,
    val muted: Int,
    val lime: Int,
    val onLime: Int,
    /** Selected chip and segment fill, and its text. */
    val inverse: Int,
    val onInverse: Int,
    val tileTints: List<Int>,
    val shadowAlpha: Float,
    val badgeFill: Int,
    val badgeText: Int,
) {
    fun tint(id: Long): Int = tileTints[(id % tileTints.size).toInt()]

    companion object {
        fun of(dark: Boolean): KeyboardPalette {
            val scheme = stickerColorScheme(dark)
            val c = stickerColors(dark)
            return KeyboardPalette(
                dark = dark,
                panel = c.keyboardPanel.toArgb(),
                raised = c.raised.toArgb(),
                line = c.line.toArgb(),
                text = scheme.onSurface.toArgb(),
                muted = c.muted.toArgb(),
                lime = c.lime.toArgb(),
                onLime = c.onLime.toArgb(),
                inverse = scheme.inverseSurface.toArgb(),
                onInverse = scheme.inverseOnSurface.toArgb(),
                tileTints = c.tileTints.map { it.toArgb() },
                shadowAlpha = c.stickerShadowAlpha,
                badgeFill = c.badgeFill.toArgb(),
                badgeText = c.badgeText.toArgb(),
            )
        }

        /** Follows the system dark setting, like the app (a forced theme arrives with Settings in M5). */
        fun of(context: Context): KeyboardPalette {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return of(night == Configuration.UI_MODE_NIGHT_YES)
        }
    }
}
