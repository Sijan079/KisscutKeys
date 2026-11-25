package app.kisscut.keys.licenses

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kisscut.keys.TestLibrary
import app.kisscut.keys.common.LocalThumbnails
import app.kisscut.keys.common.PartTags
import app.kisscut.keys.nav.AppNav
import app.kisscut.keys.settings.SettingsTags
import app.kisscut.keys.ui.theme.StickersTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings, Open-source licenses: the list, one license from the real assets, and Back. */
@RunWith(AndroidJUnit4::class)
class LicensesUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val library = TestLibrary()

    @After
    fun tearDown() = library.close()

    @Test
    fun settingsOpensTheLicensesAndBackReturns() {
        rule.setContent {
            StickersTheme(dark = true) {
                CompositionLocalProvider(LocalThumbnails provides { null }) {
                    AppNav(backend = library.backend, onAdd = {})
                }
            }
        }
        rule.onNodeWithTag(PartTags.NAV_SETTINGS).performClick()
        rule.onNodeWithTag(SettingsTags.LICENSES).performScrollTo().performClick()
        for (license in BUNDLED_LICENSES) rule.onNodeWithTag(LicensesTags.row(license.file)).assertIsDisplayed()

        rule.onNodeWithTag(LicensesTags.row("Apache-2.0-AOSP-LatinIME.txt")).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTagText(LicensesTags.TEXT, "Android Open Source Project")
        }
        rule.onNodeWithTag(LicensesTags.TEXT).assertTextContains("Apache License", substring = true)

        rule.onNodeWithTag(LicensesTags.BACK).performClick()
        rule.onNodeWithTag(LicensesTags.row("OFL-Figtree.txt")).assertIsDisplayed()
        rule.onNodeWithTag(LicensesTags.BACK).performClick()
        rule.onNodeWithTag(SettingsTags.LICENSES).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagText(
        tag: String,
        text: String,
    ): Boolean = onAllNodes(
        androidx.compose.ui.test.hasTestTag(tag) and androidx.compose.ui.test.hasText(text, substring = true),
    ).fetchSemanticsNodes().isNotEmpty()
}
