package app.kisscut.keys.keyboard

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import app.kisscut.keys.settings.KeyboardSettings

/**
 * The click and buzz of a key press, as set in Settings (docs/KEYBOARD.md, Keyboard settings). [settings] is
 * read on every tap, so a change applies at once. The sound uses the volume form of `playSoundEffect`, which
 * ignores the phone's Touch sounds switch (this setting is the switch) and plays a little under media volume.
 */
class KeyFeedback(context: Context, private val settings: () -> KeyboardSettings) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun tap(view: View) {
        val now = settings()
        if (now.vibration) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (now.keySound) audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
    }
}
