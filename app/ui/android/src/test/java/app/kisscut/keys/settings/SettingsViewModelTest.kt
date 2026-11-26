package app.kisscut.keys.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeThemes : ThemeStore {
        override val theme = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setTheme(mode: ThemeMode) {
            theme.value = mode
        }
    }

    private class FakeKeyboard : KeyboardSettingsStore {
        override val settings = MutableStateFlow(KeyboardSettings())
        override suspend fun set(settings: KeyboardSettings) {
            this.settings.value = settings
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsThemeAndStorageAndMeasuresAgainWhenTheLibraryChanges() = runTest(dispatcher) {
        val themes = FakeThemes()
        val count = MutableStateFlow(2)
        var bytes = 100L
        val vm = SettingsViewModel(themes, FakeKeyboard(), count) { bytes }
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.SYSTEM, 2, 100), vm.state.value)

        bytes = 250
        count.value = 3
        vm.setTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.DARK, 3, 250), vm.state.value)
    }

    @Test
    fun keyboardSettingsDefaultToVibrationOnlyAndSaveChanges() = runTest(dispatcher) {
        val keyboard = FakeKeyboard()
        val vm = SettingsViewModel(FakeThemes(), keyboard, MutableStateFlow(0)) { 0 }
        advanceUntilIdle()
        assertEquals(
            KeyboardSettings(keySound = false, vibration = true, numberRow = false, toolbar = false),
            vm.state.value.keyboard,
        )
        vm.setKeyboard(
            KeyboardSettings(keySound = true, numberRow = true, height = KeyboardHeight.LARGE, toolbar = true),
        )
        advanceUntilIdle()
        assertEquals(KeyboardHeight.LARGE, keyboard.settings.value.height)
        assertTrue(vm.state.value.keyboard.keySound)
        assertTrue(vm.state.value.keyboard.numberRow)
        assertTrue(vm.state.value.keyboard.toolbar)
    }

    @Test
    fun clearingLearnedWordsAsksFirstThenForgetsThem() = runTest(dispatcher) {
        val learned = MutableStateFlow(7)
        var cleared = 0
        val vm = SettingsViewModel(
            FakeThemes(),
            FakeKeyboard(),
            MutableStateFlow(0),
            learnedCount = learned,
            forgetLearned = {
                cleared++
                learned.value = 0
            },
        ) { 0L }
        advanceUntilIdle()
        assertEquals(7, vm.state.value.learnedWords)
        assertTrue(vm.state.value.keyboard.learnWords, "learning is on by default")

        vm.askClearLearned()
        advanceUntilIdle()
        assertTrue(vm.state.value.confirmClearLearned)
        vm.dismissClearLearned()
        advanceUntilIdle()
        assertFalse(vm.state.value.confirmClearLearned)
        assertEquals(0, cleared)

        vm.askClearLearned()
        vm.clearLearned()
        advanceUntilIdle()
        assertEquals(1, cleared)
        assertEquals(0, vm.state.value.learnedWords)
        assertFalse(vm.state.value.confirmClearLearned)
    }

    @Test
    fun keyboardHeights() {
        assertEquals(KeyboardHeight.SMALL, KeyboardHeight.of("small"))
        assertEquals(KeyboardHeight.NORMAL, KeyboardHeight.of("huge"))
        assertEquals(KeyboardHeight.NORMAL, KeyboardHeight.of(null))
        assertTrue(KeyboardHeight.SMALL.scale < 1f && KeyboardHeight.LARGE.scale > 1f)
    }

    @Test
    fun themeModes() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertEquals(ThemeMode.LIGHT, ThemeMode.of("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of("sepia"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of(null))
    }
}
