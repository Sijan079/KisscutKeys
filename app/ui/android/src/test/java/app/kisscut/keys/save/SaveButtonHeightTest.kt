package app.kisscut.keys.save

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

class SaveButtonHeightTest {
    @Test
    fun shortPhonesGetTheTouchTargetMinimum() {
        assertEquals(48.dp, saveButtonHeight(widthDp = 360, heightDp = 640))
    }

    @Test
    fun regularPhonesMatchTheDesign() {
        assertEquals(56.dp, saveButtonHeight(widthDp = 411, heightDp = 914))
        assertEquals(56.dp, saveButtonHeight(widthDp = 390, heightDp = 700))
    }

    @Test
    fun tabletsGetABiggerButton() {
        assertEquals(64.dp, saveButtonHeight(widthDp = 800, heightDp = 1280))
    }

    @Test
    fun landscapePhoneIsShortSoItStaysSmall() {
        assertEquals(48.dp, saveButtonHeight(widthDp = 914, heightDp = 411))
    }
}
