package com.example.stickers.debug

import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat

/**
 * Debug-only: logs which content types each text field accepts from keyboards, so the send rule can be checked
 * against real chat apps (docs/KEYBOARD.md, Real-app checks). Read with `adb logcat -s StickerFieldTypes`.
 * Logs only the app's package name and the type list, never anything typed. Release builds use a no-op.
 */
object FieldTypesLog {
    const val TAG = "StickerFieldTypes"

    fun log(info: EditorInfo) {
        Log.d(TAG, line(info.packageName, EditorInfoCompat.getContentMimeTypes(info)))
    }

    fun line(packageName: String?, types: Array<String>): String {
        val list = if (types.isEmpty()) "no content types" else types.joinToString(", ")
        return "${packageName ?: "unknown app"}: $list"
    }
}
