package app.kisscut.keys.keyboard

import android.text.InputType

/** Password and PIN fields: the keyboard never sends or copies anything into them. */
fun isPasswordField(inputType: Int): Boolean {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

/**
 * Fields where suggestions and autocorrect may run (docs/KEYBOARD.md, Typing pipeline): plain text only. Never in
 * password, number, phone, date, email or web address fields, in fields that ask for no suggestions, or in fields
 * with no input type (those can't show the underlined word being typed).
 */
fun allowsTypingAids(inputType: Int): Boolean {
    if (inputType == InputType.TYPE_NULL || isPasswordField(inputType)) return false
    if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
    if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
    return when (inputType and InputType.TYPE_MASK_VARIATION) {
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_URI,
        -> false
        else -> true
    }
}
