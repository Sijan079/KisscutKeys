package app.kisscut.keys.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputFieldsTest {
    private val text = InputType.TYPE_CLASS_TEXT
    private val number = InputType.TYPE_CLASS_NUMBER

    @Test
    fun passwordVariationsAreDetected() {
        assertTrue(isPasswordField(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(isPasswordField(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertTrue(isPasswordField(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertTrue(isPasswordField(number or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test
    fun otherFieldsAreNotPasswords() {
        assertFalse(isPasswordField(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(isPasswordField(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(isPasswordField(number))
        assertFalse(isPasswordField(InputType.TYPE_NULL))
        // 0x80 is the password variation only for text; on a number field it is not.
        assertFalse(isPasswordField(number or InputType.TYPE_TEXT_VARIATION_PASSWORD))
    }

    @Test
    fun typingAidsOnlyInPlainTextFields() {
        assertTrue(allowsTypingAids(text))
        val chat = text or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        assertTrue(allowsTypingAids(chat))
        assertTrue(allowsTypingAids(text or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
        for (off in listOf(
            InputType.TYPE_NULL,
            text or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            text or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            text or InputType.TYPE_TEXT_VARIATION_URI,
            text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            number,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
        )) {
            assertFalse(allowsTypingAids(off), "inputType $off")
        }
    }

    @Test
    fun learningOnlyWhereTypingAidsRunAndNotInIncognito() {
        assertTrue(allowsLearning(text, EditorInfo.IME_ACTION_SEND))
        assertFalse(allowsLearning(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertFalse(allowsLearning(text or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0))
        assertFalse(allowsLearning(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 0))
        assertFalse(allowsLearning(number, 0))
    }
}
