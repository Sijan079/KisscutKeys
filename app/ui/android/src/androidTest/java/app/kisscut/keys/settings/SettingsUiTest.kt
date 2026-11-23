package app.kisscut.keys.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kisscut.keys.backup.BackupDialogs
import app.kisscut.keys.common.PartTags
import app.kisscut.keys.backup.BackupTags
import app.kisscut.keys.backup.BackupViewModel
import app.kisscut.keys.backup.PackBackend
import app.kisscut.keys.backup.Progress
import app.kisscut.keys.shared.core.RejectReason
import app.kisscut.keys.shared.core.pack.ExportResult
import app.kisscut.keys.shared.core.pack.ImportSummary
import app.kisscut.keys.shared.core.pack.PackException
import app.kisscut.keys.shared.core.pack.PackProblem
import app.kisscut.keys.shared.core.pack.PackSettings
import app.kisscut.keys.ui.theme.StickersTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Settings screen and the backup dialogs on the device (docs/UI_SPEC.md, Settings). */
@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private class Themes : ThemeStore {
        override val theme = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setTheme(mode: ThemeMode) {
            theme.value = mode
        }
    }

    private class Backend(var failImport: Boolean = false) : PackBackend {
        override suspend fun backUp(uri: String, settings: PackSettings, onProgress: Progress) =
            ExportResult(stickers = 12, collections = 2, bytes = 1)

        override suspend fun exportForShare(collectionId: Long, name: String, onProgress: Progress) = "content://x"

        override suspend fun import(uri: String, onProgress: Progress): ImportSummary {
            if (failImport) throw PackException(PackProblem.TooNew(9))
            return ImportSummary(
                added = 5, duplicates = 2, rejected = mapOf(RejectReason.TOO_LARGE to 1), missing = 0,
                collectionsCreated = 1, collectionsMerged = 1, collectionsFlattened = 0, settings = null,
            )
        }
    }

    @Test
    fun themeSegmentsAndRowsCallTheirActions() {
        var theme by mutableStateOf(ThemeMode.SYSTEM)
        val taps = mutableListOf<String>()
        rule.setContent {
            StickersTheme {
                SettingsScreen(
                    SettingsUiState(theme, stickerCount = 38, storageBytes = 9_600_000),
                    SettingsActions(
                        onTheme = { theme = it },
                        onBackUp = { taps += "backup" },
                        onShare = { taps += "share" },
                        onImport = { taps += "import" },
                    ),
                    onNavigate = {},
                    version = "1.0",
                )
            }
        }
        rule.onNodeWithTag(SettingsTags.theme(ThemeMode.SYSTEM)).assertIsSelected()
        rule.onNodeWithTag(SettingsTags.theme(ThemeMode.LIGHT)).performClick()
        rule.onNodeWithTag(SettingsTags.theme(ThemeMode.LIGHT)).assertIsSelected()
        rule.onNodeWithTag(SettingsTags.theme(ThemeMode.SYSTEM)).assertIsNotSelected()
        assertEquals(ThemeMode.LIGHT, theme)

        for (tag in listOf(SettingsTags.BACK_UP, SettingsTags.SHARE, SettingsTags.IMPORT)) {
            rule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        assertEquals(listOf("backup", "share", "import"), taps)
        rule.onNodeWithTag(SettingsTags.STORAGE).performScrollTo()
        rule.onNodeWithText("38 stickers", substring = true).assertExists()
        rule.onNodeWithTag(PartTags.NAV_ADD).assertDoesNotExist() // nothing to add from Settings
    }

    @Test
    fun keyboardSwitchesAndHeightChangeTheSettings() {
        var keyboard by mutableStateOf(KeyboardSettings())
        rule.setContent {
            StickersTheme {
                SettingsScreen(
                    SettingsUiState(keyboard = keyboard),
                    SettingsActions(onKeyboard = { keyboard = it }),
                    onNavigate = {},
                )
            }
        }
        rule.onNodeWithTag(SettingsTags.VIBRATION).performScrollTo().assertIsOn()
        rule.onNodeWithTag(SettingsTags.KEY_SOUND).performScrollTo().assertIsOff().performClick().assertIsOn()
        rule.onNodeWithTag(SettingsTags.NUMBER_ROW).performScrollTo().performClick().assertIsOn()
        rule.onNodeWithTag(SettingsTags.height(KeyboardHeight.NORMAL)).performScrollTo().assertIsSelected()
        rule.onNodeWithTag(SettingsTags.height(KeyboardHeight.LARGE)).performClick().assertIsSelected()
        assertEquals(
            KeyboardSettings(keySound = true, vibration = true, numberRow = true, height = KeyboardHeight.LARGE),
            keyboard,
        )
    }

    @Test
    fun importShowsASummaryAndFailuresSayWhy() {
        val backend = Backend()
        val vm = BackupViewModel(backend, Themes(), flowOf(emptyList()))
        rule.setContent { StickersTheme { BackupDialogs(vm) } }

        rule.runOnIdle { vm.import("content://pack") }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag(BackupTags.RESULT)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("5 stickers added").assertExists()
        rule.onNodeWithText("2 were already in your library").assertExists()
        rule.onNodeWithText("1 new collection").assertExists()
        rule.onNodeWithTag(BackupTags.OK).performClick()

        backend.failImport = true
        rule.runOnIdle { vm.import("content://pack") }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag(BackupTags.FAILED)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("newer version", substring = true).assertExists()
    }
}
