package app.kisscut.keys.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Null until counted. */
    val stickerCount: Int? = null,
    /** Bytes of stored stickers and thumbnails; null until measured. */
    val storageBytes: Long? = null,
    val keyboard: KeyboardSettings = KeyboardSettings(),
    /** How many words the keyboard has learned (docs/KEYBOARD.md, Learned words). */
    val learnedWords: Int = 0,
    /** The "Clear learned words?" dialog is open. */
    val confirmClearLearned: Boolean = false,
)

/**
 * Settings: theme, keyboard (with clearing the learned words) and the storage row. Backup and sharing live in
 * [app.kisscut.keys.backup.BackupViewModel].
 */
class SettingsViewModel(
    private val themes: ThemeStore,
    private val keyboard: KeyboardSettingsStore,
    stickerCount: Flow<Int>,
    learnedCount: Flow<Int> = flowOf(0),
    private val forgetLearned: () -> Unit = {},
    storageBytes: suspend () -> Long,
) : ViewModel() {
    private val confirming = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        themes.theme,
        keyboard.settings,
        stickerCount.map { it to storageBytes() }, // measured again whenever the library changes
        learnedCount,
        confirming,
    ) { theme, kb, (count, bytes), learned, confirm -> SettingsUiState(theme, count, bytes, kb, learned, confirm) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun askClearLearned() {
        confirming.value = true
    }

    fun dismissClearLearned() {
        confirming.value = false
    }

    /** Forgets every learned word; the running keyboard stops using them at once. */
    fun clearLearned() {
        forgetLearned()
        confirming.value = false
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { themes.setTheme(mode) }
    }

    fun setKeyboard(settings: KeyboardSettings) {
        viewModelScope.launch { keyboard.set(settings) }
    }
}
