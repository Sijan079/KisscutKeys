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

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun showsThemeAndStorageAndMeasuresAgainWhenTheLibraryChanges() = runTest(dispatcher) {
        val themes = FakeThemes()
        val count = MutableStateFlow(2)
        var bytes = 100L
        val vm = SettingsViewModel(themes, count) { bytes }
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.SYSTEM, 2, 100), vm.state.value)

        bytes = 250
        count.value = 3
        vm.setTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(SettingsUiState(ThemeMode.DARK, 3, 250), vm.state.value)
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
