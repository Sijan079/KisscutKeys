package app.kisscut.keys.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Null until counted. */
    val stickerCount: Int? = null,
    /** Bytes of stored stickers and thumbnails; null until measured. */
    val storageBytes: Long? = null,
)

/** Settings: theme and the storage row. Backup and sharing live in [app.kisscut.keys.backup.BackupViewModel]. */
class SettingsViewModel(
    private val themes: ThemeStore,
    stickerCount: Flow<Int>,
    storageBytes: suspend () -> Long,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        themes.theme,
        stickerCount.map { it to storageBytes() }, // measured again whenever the library changes
    ) { theme, (count, bytes) -> SettingsUiState(theme, count, bytes) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { themes.setTheme(mode) }
    }
}
