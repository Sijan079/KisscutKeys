package app.kisscut.keys.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kisscut.keys.R
import app.kisscut.keys.common.FloatingNav
import app.kisscut.keys.common.TopLevel
import app.kisscut.keys.ui.theme.StickerType
import app.kisscut.keys.ui.theme.StickersTheme

object SettingsTags {
    const val KEYBOARD = "settings:keyboard"
}

/** Placeholder: the real Settings screen (theme, backup) is milestone M5. Only the keyboard card for now. */
@Composable
fun SettingsScreen(onNavigate: (TopLevel) -> Unit, onAdd: () -> Unit, onKeyboardSetup: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("settings")) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = StickerType.Title,
                color = MaterialTheme.colorScheme.onBackground,
            )
            KeyboardCard(onKeyboardSetup)
            Text(stringResource(R.string.settings_soon), style = StickerType.Body, color = StickersTheme.colors.muted)
        }
        FloatingNav(TopLevel.SETTINGS, onNavigate, onAdd)
    }
}

/** Keyboard status card (Settings board): lime tint, opens Keyboard setup. */
@Composable
private fun KeyboardCard(onClick: () -> Unit) {
    val colors = StickersTheme.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = colors.limeTint,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SettingsTags.KEYBOARD),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = colors.accentText)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_keyboard_title),
                    style = StickerType.Heading,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(stringResource(R.string.settings_keyboard_body), style = StickerType.Body, color = colors.muted)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.muted)
        }
    }
}
