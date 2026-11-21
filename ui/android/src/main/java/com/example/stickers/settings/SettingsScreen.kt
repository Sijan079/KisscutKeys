package com.example.stickers.settings

import androidx.compose.foundation.background
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
import com.example.stickers.R
import com.example.stickers.common.FloatingNav
import com.example.stickers.common.TopLevel
import com.example.stickers.ui.theme.StickerType
import com.example.stickers.ui.theme.StickersTheme

/** Placeholder: the real Settings screen (theme, backup, keyboard) is milestone M5. */
@Composable
fun SettingsScreen(onNavigate: (TopLevel) -> Unit, onAdd: () -> Unit) {
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
            Text(stringResource(R.string.settings_soon), style = StickerType.Body, color = StickersTheme.colors.muted)
        }
        FloatingNav(TopLevel.SETTINGS, onNavigate, onAdd)
    }
}
