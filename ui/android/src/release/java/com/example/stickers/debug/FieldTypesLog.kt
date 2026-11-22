package com.example.stickers.debug

import android.view.inputmethod.EditorInfo

/** Release no-op for the debug-only field type log (see the debug source set). */
object FieldTypesLog {
    @Suppress("UNUSED_PARAMETER")
    fun log(info: EditorInfo) = Unit
}
