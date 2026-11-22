package app.kisscut.keys.setup

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kisscut.keys.R
import app.kisscut.keys.TestLibrary
import app.kisscut.keys.common.LocalThumbnails
import app.kisscut.keys.nav.AppNav
import app.kisscut.keys.settings.SettingsTags
import app.kisscut.keys.common.PartTags
import app.kisscut.keys.ui.theme.StickersTheme
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class KeyboardSetupUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val library = TestLibrary()
    private val status = MutableStateFlow(KeyboardStatus(enabled = false, selected = false))
    private val store = object : SetupStore {
        override val dismissed = MutableStateFlow(false)
        override suspend fun dismiss() {
            dismissed.value = true
        }
    }
    private val source = object : KeyboardStatusSource {
        override fun observe() = status
        override fun refresh() = Unit
    }
    private val actions = mutableListOf<SetupAction>()

    @After
    fun tearDown() = library.close()

    private fun setContent(startAtSetup: Boolean) {
        rule.setContent {
            StickersTheme(dark = true) {
                CompositionLocalProvider(LocalThumbnails provides { null }) {
                    AppNav(
                        backend = library.backend,
                        onAdd = {},
                        startAtSetup = startAtSetup,
                        setupScreen = { leave ->
                            val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                                KeyboardSetupViewModel(source, library.stickers.observeAll().map { it.size }, store)
                            }
                            KeyboardSetupScreen(vm, onAction = { actions += it }, onLeave = leave)
                        },
                    )
                }
            }
        }
    }

    private fun stepState(i: Int, res: Int) = rule.onNodeWithTag(SetupTags.step(i))
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(res)))

    @Test
    fun buttonAndStepsFollowTheKeyboardState() {
        setContent(startAtSetup = true)
        rule.onNodeWithTag(SetupTags.SCREEN).assertIsDisplayed()
        stepState(0, R.string.setup_state_current)
        rule.onNodeWithTag(SetupTags.ACTION).assert(hasText(context.getString(R.string.setup_open_settings)))
        rule.onNodeWithTag(SetupTags.ACTION).performClick()
        assertEquals(listOf(SetupAction.OPEN_SETTINGS), actions)

        status.value = KeyboardStatus(enabled = true, selected = false)
        rule.waitForIdle()
        stepState(0, R.string.setup_state_done)
        stepState(1, R.string.setup_state_current)
        rule.onNodeWithTag(SetupTags.ACTION).assert(hasText(context.getString(R.string.setup_pick_keyboard)))

        status.value = KeyboardStatus(enabled = true, selected = true)
        rule.waitForIdle()
        rule.onNodeWithTag(SetupTags.ACTION).assert(hasText(context.getString(R.string.setup_add_stickers)))
    }

    @Test
    fun skipIsRememberedAndOpensTheLibrary() {
        setContent(startAtSetup = true)
        rule.onNodeWithTag(SetupTags.SKIP).performClick()
        rule.waitUntil(5_000) { store.dismissed.value }
        rule.onNodeWithTag(PartTags.NAV_LIBRARY).assertIsDisplayed()
        assertTrue(store.dismissed.value)
    }

    @Test
    fun settingsCardOpensSetupAndBackReturns() {
        setContent(startAtSetup = false)
        rule.onNodeWithTag(PartTags.NAV_SETTINGS).performClick()
        rule.onNodeWithTag(SettingsTags.KEYBOARD).performClick()
        rule.onNodeWithTag(SetupTags.SCREEN).assertIsDisplayed()
        rule.onNodeWithTag(SetupTags.SKIP).performClick()
        rule.waitUntil(5_000) { store.dismissed.value }
        rule.onNodeWithTag(SettingsTags.KEYBOARD).assertIsDisplayed()
    }
}
