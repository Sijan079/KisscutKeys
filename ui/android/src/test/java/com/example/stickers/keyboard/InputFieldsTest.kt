package com.example.stickers.keyboard

import android.text.InputType
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
}
