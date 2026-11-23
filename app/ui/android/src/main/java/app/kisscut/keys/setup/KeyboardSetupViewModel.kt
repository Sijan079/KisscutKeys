package app.kisscut.keys.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the sticker keyboard is switched on in system settings, and whether it is the current keyboard. */
data class KeyboardStatus(val enabled: Boolean, val selected: Boolean)

/** Where the setup screen reads keyboard state; the Android one watches system settings. */
interface KeyboardStatusSource {
    /** Emits now and again whenever the user enables or switches keyboards. */
    fun observe(): Flow<KeyboardStatus>

    /** Re-reads now (for example when the screen comes back to the front). */
    fun refresh()
}

/** Remembers that the user finished or skipped setup, so it is not shown at every launch. */
interface SetupStore {
    val dismissed: Flow<Boolean>
    suspend fun dismiss()
}

enum class SetupAction { OPEN_SETTINGS, PICK_KEYBOARD, ADD_STICKERS, DONE }

enum class StepState { DONE, CURRENT, LATER }

data class SetupUiState(
    val loaded: Boolean = false,
    /** Enable, choose, add stickers: in that order. */
    val steps: List<StepState> = listOf(StepState.CURRENT, StepState.LATER, StepState.LATER),
    val action: SetupAction = SetupAction.OPEN_SETTINGS,
) {
    val allDone: Boolean get() = action == SetupAction.DONE
}

/** The steps follow the real state; the first one not done is current and decides the main button. */
fun setupState(status: KeyboardStatus, hasStickers: Boolean): SetupUiState {
    val done = listOf(status.enabled, status.enabled && status.selected, hasStickers)
    val current = done.indexOfFirst { !it }
    val steps = done.mapIndexed { i, d ->
        when {
            d -> StepState.DONE
            i == current -> StepState.CURRENT
            else -> StepState.LATER
        }
    }
    val action = when (current) {
        0 -> SetupAction.OPEN_SETTINGS
        1 -> SetupAction.PICK_KEYBOARD
        2 -> SetupAction.ADD_STICKERS
        else -> SetupAction.DONE
    }
    return SetupUiState(loaded = true, steps = steps, action = action)
}

/** Keyboard setup (UI_SPEC screen 1): shows the state of each step, updated when the user comes back. */
class KeyboardSetupViewModel(
    private val status: KeyboardStatusSource,
    stickerCount: Flow<Int>,
    private val store: SetupStore,
) : ViewModel() {
    val state: StateFlow<SetupUiState> = combine(status.observe(), stickerCount) { s, count ->
        setupState(s, count > 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    fun refresh() = status.refresh()

    /** Skip or Done: remember it, then [then] leaves the screen. */
    fun dismiss(then: () -> Unit) {
        viewModelScope.launch {
            store.dismiss()
            then()
        }
    }
}
