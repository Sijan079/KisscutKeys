package com.example.stickers.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

/**
 * Debug-only target for the sticker keyboard (docs/KEYBOARD.md, Testing): four fields that accept
 * any image, PNG only, no images, and a password. Received content is recorded in [received].
 */
class ImeTestActivity : Activity() {

    data class Received(val field: String, val uri: Uri, val mimeType: String, val resolvedType: String?)

    lateinit var anyImage: EditText
        private set
    lateinit var pngOnly: EditText
        private set
    lateinit var plain: EditText
        private set
    lateinit var password: EditText
        private set
    lateinit var send: EditText
        private set

    /** Editor actions the Send field received (Enter key). */
    val actions: MutableList<Int> = java.util.Collections.synchronizedList(mutableListOf())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 96, 32, 32)
        }
        anyImage = ContentField(this, FIELD_ANY, arrayOf("image/*"))
        pngOnly = ContentField(this, FIELD_PNG, arrayOf("image/png"))
        plain = EditText(this).apply { tag = FIELD_PLAIN; hint = FIELD_PLAIN }
        password = EditText(this).apply {
            tag = FIELD_PASSWORD
            hint = FIELD_PASSWORD
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        send = EditText(this).apply {
            tag = FIELD_SEND
            hint = FIELD_SEND
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ ->
                actions += action
                true
            }
        }
        column.addView(TextView(this).apply { text = "Keyboard test" })
        listOf(anyImage, pngOnly, plain, password, send).forEach { column.addView(it) }
        setContentView(column)
    }

    /** An EditText that tells the keyboard it accepts [mimeTypes] and records what it gets. */
    @SuppressLint("ViewConstructor")
    private class ContentField(context: Context, private val name: String, private val mimeTypes: Array<String>) :
        EditText(context) {
        init {
            tag = name
            hint = name
        }

        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val base = super.onCreateInputConnection(outAttrs) ?: return null
            EditorInfoCompat.setContentMimeTypes(outAttrs, mimeTypes)
            return InputConnectionCompat.createWrapper(base, outAttrs) { info, flags, _ ->
                if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                    runCatching { info.requestPermission() }.onFailure { return@createWrapper false }
                }
                val type = info.description.getMimeType(0)
                val resolved = runCatching { context.contentResolver.getType(info.contentUri) }.getOrNull()
                received += Received(name, info.contentUri, type, resolved)
                true
            }
        }
    }

    companion object {
        const val FIELD_ANY = "any-image"
        const val FIELD_PNG = "png-only"
        const val FIELD_PLAIN = "plain"
        const val FIELD_PASSWORD = "password"
        const val FIELD_SEND = "send"

        /** Everything the image fields received, oldest first. */
        val received: MutableList<Received> = java.util.Collections.synchronizedList(mutableListOf())
    }
}
