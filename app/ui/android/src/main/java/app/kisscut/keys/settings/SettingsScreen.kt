package app.kisscut.keys.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    const val TOOLBAR = "settings:kb-toolbar"
    const val AUTOCORRECT = "settings:kb-autocorrect"
    const val SUGGESTIONS = "settings:kb-suggestions"
    const val LEARN_WORDS = "settings:kb-learn-words"
    const val CLEAR_LEARNED = "settings:kb-clear-learned"
    const val CLEAR_LEARNED_CONFIRM = "settings:kb-clear-learned:confirm"
    const val SWIPE = "settings:kb-swipe"
    fun trail(thickness: TrailThickness) = "settings:kb-trail:" + thickness.key
    const val LICENSES = "settings:licenses"
    fun height(height: KeyboardHeight) = "settings:kb-height:" + height.key
}

/** What the Settings rows do; the activity wires them to the file pickers and the backup dialogs. */
data class SettingsActions(
    val onTheme: (ThemeMode) -> Unit = {},
    val onKeyboard: (KeyboardSettings) -> Unit = {},
    val onKeyboardSetup: () -> Unit = {},
    val onClearLearned: () -> Unit = {},
    val onConfirmClearLearned: () -> Unit = {},
    val onDismissClearLearned: () -> Unit = {},
    val onBackUp: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onLicenses: () -> Unit = {},
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
            KeyboardGroup(state.keyboard, state.learnedWords, actions.onKeyboard, actions.onClearLearned)

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

            SectionTitle(stringResource(R.string.settings_about))
            Group {
                ActionRow(
                    icon = Icons.Outlined.Description,
                    title = R.string.settings_licenses,
                    body = R.string.settings_licenses_body,
                    tag = SettingsTags.LICENSES,
                    onClick = actions.onLicenses,
                )
            }

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
    if (state.confirmClearLearned) ClearLearnedDialog(state.learnedWords, actions)
}

@Composable
private fun ClearLearnedDialog(count: Int, actions: SettingsActions) = AlertDialog(
    onDismissRequest = actions.onDismissClearLearned,
    title = { Text(stringResource(R.string.settings_kb_clear_title)) },
    text = { Text(pluralStringResource(R.plurals.settings_kb_clear_body, count, count), style = StickerType.Body) },
    confirmButton = {
        TextButton(
            onClick = actions.onConfirmClearLearned,
            modifier = Modifier.testTag(SettingsTags.CLEAR_LEARNED_CONFIRM),
        ) {
            Text(stringResource(R.string.settings_kb_clear_confirm), color = StickersTheme.colors.danger)
        }
    },
    dismissButton = {
        TextButton(onClick = actions.onDismissClearLearned) { Text(stringResource(R.string.cancel)) }
    },
)

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

/**
 * Key sound, vibration, number row, autocorrect, learning, swipe and toolbar switches, then the keyboard height.
 * Applied at the next text field. "Clear learned words" hangs under Learn new words while any are learned.
 */
@Composable
private fun KeyboardGroup(
    settings: KeyboardSettings,
    learnedWords: Int,
    onChange: (KeyboardSettings) -> Unit,
    onClearLearned: () -> Unit,
) {
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
        SwitchRow(R.string.settings_kb_autocorrect, settings.autocorrect, SettingsTags.AUTOCORRECT) {
            onChange(settings.copy(autocorrect = it))
        }
        // Suggestions sits under Autocorrect and only shows while it is off: Autocorrect always shows suggestions.
        // The stored choice comes back when Autocorrect is turned off.
        if (!settings.autocorrect) {
            SwitchRow(R.string.settings_kb_suggestions, settings.suggestions, SettingsTags.SUGGESTIONS, nested = true) {
                onChange(settings.copy(suggestions = it))
            }
        }
        HorizontalDivider(color = StickersTheme.colors.line)
        SwitchRow(R.string.settings_kb_learn, settings.learnWords, SettingsTags.LEARN_WORDS) {
            onChange(settings.copy(learnWords = it))
        }
        if (learnedWords > 0) ClearLearnedRow(learnedWords, onClearLearned)
        HorizontalDivider(color = StickersTheme.colors.line)
        SwitchRow(R.string.settings_kb_swipe, settings.swipe, SettingsTags.SWIPE) {
            onChange(settings.copy(swipe = it))
        }
        // Trail thickness belongs to Swipe typing: indented under it and shown only while it is on.
        if (settings.swipe) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .branch(StickersTheme.colors.muted, turnAt = BRANCH_TITLE_MID)
                    .padding(start = NESTED_INDENT, end = 16.dp, top = 8.dp, bottom = 12.dp),
            ) {
                Text(
                    stringResource(R.string.settings_kb_trail),
                    style = StickerType.Body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Segments(
                    options = listOf(
                        TrailThickness.THIN to R.string.settings_kb_trail_thin,
                        TrailThickness.MEDIUM to R.string.settings_kb_trail_medium,
                        TrailThickness.THICK to R.string.settings_kb_trail_thick,
                    ),
                    selected = settings.trail,
                    tag = SettingsTags::trail,
                    onPick = { onChange(settings.copy(trail = it)) },
                )
            }
        }
        HorizontalDivider(color = StickersTheme.colors.line)
        SwitchRow(R.string.settings_kb_toolbar, settings.toolbar, SettingsTags.TOOLBAR) {
            onChange(settings.copy(toolbar = it))
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

/** Start indent of a setting that belongs to the one above it (Suggestions under Autocorrect). */
private val NESTED_INDENT = 48.dp

/** Where the tree line of a nested row runs down, and where it turns into the row. */
private val BRANCH_X = 22.dp
private val BRANCH_END = 38.dp

/** The line starts this far up, inside the row above, so it hangs from that row's title. */
private val BRANCH_REACH = 14.dp

/** Where the line turns into a nested block whose title sits at its top (not its middle). */
private val BRANCH_TITLE_MID = 20.dp

/**
 * The tree line of a nested setting: it runs down from the row above and turns right into this one, at [turnAt]
 * from the top, or halfway down when null.
 */
private fun Modifier.branch(color: Color, turnAt: Dp? = null) = drawBehind {
    val x = BRANCH_X.toPx()
    val turn = turnAt?.toPx() ?: (size.height / 2)
    val stroke = 1.5.dp.toPx()
    drawLine(color, Offset(x, -BRANCH_REACH.toPx()), Offset(x, turn), stroke, StrokeCap.Round)
    drawLine(color, Offset(x, turn), Offset(BRANCH_END.toPx(), turn), stroke, StrokeCap.Round)
}

/**
 * A row with a title and a switch; the whole row toggles it. A [nested] row is indented under the one above, with
 * no divider between them: a line runs down from the row above and turns into this one, like a tree.
 */
@Composable
private fun SwitchRow(title: Int, checked: Boolean, tag: String, nested: Boolean = false, onChange: (Boolean) -> Unit) {
    val branch = StickersTheme.colors.muted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (nested) Modifier.branch(branch) else Modifier)
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = if (nested) NESTED_INDENT else 16.dp, end = 16.dp)
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

/** "Clear learned words" and how many there are, nested under Learn new words; asks before clearing. */
@Composable
private fun ClearLearnedRow(count: Int, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .branch(StickersTheme.colors.muted)
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = NESTED_INDENT, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .testTag(SettingsTags.CLEAR_LEARNED),
    ) {
        Text(
            stringResource(R.string.settings_kb_clear_learned),
            style = StickerType.Body,
            color = StickersTheme.colors.danger,
        )
        Text(
            pluralStringResource(R.plurals.settings_kb_learned_count, count, count),
            style = StickerType.Label,
            color = StickersTheme.colors.muted,
        )
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
