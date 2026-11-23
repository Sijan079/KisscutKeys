package app.kisscut.keys.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kisscut.keys.R
import app.kisscut.keys.common.FloatingNav
import app.kisscut.keys.common.TopLevel
import app.kisscut.keys.ui.theme.StickerSizes
import app.kisscut.keys.ui.theme.StickerType
import app.kisscut.keys.ui.theme.StickersTheme

object SettingsTags {
    const val KEYBOARD = "settings:keyboard"
    const val BACK_UP = "settings:back-up"
    const val SHARE = "settings:share"
    const val IMPORT = "settings:import"
    const val STORAGE = "settings:storage"
    fun theme(mode: ThemeMode) = "settings:theme:" + mode.key
    const val KEY_SOUND = "settings:kb-sound"
    const val VIBRATION = "settings:kb-vibration"
    const val NUMBER_ROW = "settings:kb-number-row"
    fun height(height: KeyboardHeight) = "settings:kb-height:" + height.key
}

/** What the Settings rows do; the activity wires them to the file pickers and the backup dialogs. */
data class SettingsActions(
    val onTheme: (ThemeMode) -> Unit = {},
    val onKeyboard: (KeyboardSettings) -> Unit = {},
    val onKeyboardSetup: () -> Unit = {},
    val onBackUp: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onImport: () -> Unit = {},
)

/** Settings (docs/UI_SPEC.md, Settings board): keyboard card, theme, keyboard, backup and sharing, storage. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    onNavigate: (TopLevel) -> Unit,
    version: String = "",
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("settings")) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = StickerSizes.NavClearance),
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = StickerType.Title,
                color = MaterialTheme.colorScheme.onBackground,
            )
            KeyboardCard(actions.onKeyboardSetup)

            SectionTitle(stringResource(R.string.settings_theme))
            ThemeSegments(state.theme, actions.onTheme)

            SectionTitle(stringResource(R.string.settings_keyboard))
            KeyboardGroup(state.keyboard, actions.onKeyboard)

            SectionTitle(stringResource(R.string.settings_backup))
            Group {
                ActionRow(
                    icon = Icons.Outlined.Upload,
                    title = R.string.backup_everything,
                    body = R.string.backup_everything_body,
                    tag = SettingsTags.BACK_UP,
                    onClick = actions.onBackUp,
                )
                HorizontalDivider(color = StickersTheme.colors.line)
                ActionRow(
                    icon = Icons.Outlined.Share,
                    title = R.string.backup_share,
                    body = R.string.backup_share_body,
                    tag = SettingsTags.SHARE,
                    onClick = actions.onShare,
                )
                HorizontalDivider(color = StickersTheme.colors.line)
                ActionRow(
                    icon = Icons.Outlined.Download,
                    title = R.string.backup_import,
                    body = R.string.backup_import_body,
                    tag = SettingsTags.IMPORT,
                    onClick = actions.onImport,
                )
            }

            SectionTitle(stringResource(R.string.settings_storage))
            Group { StorageRow(state) }

            if (version.isNotEmpty()) {
                Text(
                    stringResource(R.string.settings_version, version),
                    style = StickerType.Body,
                    color = StickersTheme.colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        FloatingNav(TopLevel.SETTINGS, onNavigate, onAdd = null)
    }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text,
    style = StickerType.Heading,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.padding(top = 6.dp).semantics { heading() },
)

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

/** System, Dark, Light; the selected segment uses the inverted fill. */
@Composable
private fun ThemeSegments(selected: ThemeMode, onPick: (ThemeMode) -> Unit) = Segments(
    options = listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.DARK to R.string.theme_dark,
        ThemeMode.LIGHT to R.string.theme_light,
    ),
    selected = selected,
    tag = SettingsTags::theme,
    onPick = onPick,
)

/** Key sound, vibration and number row switches, then the keyboard height. Applied at the next text field. */
@Composable
private fun KeyboardGroup(settings: KeyboardSettings, onChange: (KeyboardSettings) -> Unit) {
    Group {
        SwitchRow(R.string.settings_kb_sound, settings.keySound, SettingsTags.KEY_SOUND) {
            onChange(settings.copy(keySound = it))
        }
        HorizontalDivider(color = StickersTheme.colors.line)
        SwitchRow(R.string.settings_kb_vibration, settings.vibration, SettingsTags.VIBRATION) {
            onChange(settings.copy(vibration = it))
        }
        HorizontalDivider(color = StickersTheme.colors.line)
        SwitchRow(R.string.settings_kb_number_row, settings.numberRow, SettingsTags.NUMBER_ROW) {
            onChange(settings.copy(numberRow = it))
        }
        HorizontalDivider(color = StickersTheme.colors.line)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.settings_kb_height),
                style = StickerType.Body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Segments(
                options = listOf(
                    KeyboardHeight.SMALL to R.string.settings_kb_height_small,
                    KeyboardHeight.NORMAL to R.string.settings_kb_height_normal,
                    KeyboardHeight.LARGE to R.string.settings_kb_height_large,
                ),
                selected = settings.height,
                tag = SettingsTags::height,
                onPick = { onChange(settings.copy(height = it)) },
            )
        }
    }
}

/** A row with a title and a switch; the whole row toggles it. */
@Composable
private fun SwitchRow(title: Int, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp)
            .testTag(tag),
    ) {
        Text(
            stringResource(title),
            style = StickerType.Body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // The row handles the tap and TalkBack; the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A segmented choice; the selected segment uses the inverted fill. */
@Composable
private fun <T> Segments(options: List<Pair<T, Int>>, selected: T, tag: (T) -> String, onPick: (T) -> Unit) {
    val colors = StickersTheme.colors
    Surface(shape = RoundedCornerShape(26.dp), color = colors.raised, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((mode, label) in options) {
                val on = mode == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = StickerSizes.MinTouchTarget)
                        .background(
                            if (on) MaterialTheme.colorScheme.inverseSurface else colors.raised,
                            RoundedCornerShape(22.dp),
                        )
                        .selectable(selected = on, role = Role.RadioButton) { onPick(mode) }
                        .testTag(tag(mode)),
                ) {
                    Text(
                        stringResource(label),
                        style = StickerType.Button,
                        color = if (on) MaterialTheme.colorScheme.inverseOnSurface else colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

/** A tappable row with a violet icon tile. */
@Composable
private fun ActionRow(icon: ImageVector, title: Int, body: Int, tag: String, onClick: () -> Unit) {
    val colors = StickersTheme.colors
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            IconTile(icon)
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = StickerType.Body, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(body), style = StickerType.Label, color = colors.muted)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.muted)
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    val colors = StickersTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        // A soft violet tile behind a violet icon (Settings board).
        modifier = Modifier.size(40.dp).background(colors.violet.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
    ) {
        Icon(icon, contentDescription = null, tint = colors.violetIcon, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun StorageRow(state: SettingsUiState) {
    val context = LocalContext.current
    val count = state.stickerCount
    val bytes = state.storageBytes
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(SettingsTags.STORAGE),
    ) {
        IconTile(Icons.Outlined.SdStorage)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_storage_title),
                style = StickerType.Body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (count != null && bytes != null) {
                Text(
                    stringResource(
                        R.string.settings_storage_value,
                        pluralStringResource(R.plurals.settings_storage_stickers, count, count),
                        Formatter.formatShortFileSize(context, bytes),
                    ),
                    style = StickerType.Label,
                    color = StickersTheme.colors.muted,
                )
            }
        }
    }
}
