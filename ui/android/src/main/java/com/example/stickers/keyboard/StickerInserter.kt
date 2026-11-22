package com.example.stickers.keyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.example.stickers.R
import com.example.stickers.shared.core.InsertRoute
import com.example.stickers.shared.core.planInsert
import com.example.stickers.shared.db.Sticker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What happened to a tapped sticker; the panel turns it into a hint. */
enum class InsertOutcome {
    /** The app took it (commitContent returned true). Only this counts as a use. */
    SENT,

    /** Copied to the clipboard: the field takes no images. */
    COPIED,

    /** Copied to the clipboard: the field takes images, but not animated ones. */
    COPIED_TYPE_REJECTED,

    /** Password field: nothing sent or copied. */
    BLOCKED,

    /** The file is missing or could not be shared. */
    FAILED,
}

/**
 * Sends a sticker to the app being typed in (docs/KEYBOARD.md, Inserting a sticker): commit-content when the
 * field accepts the type, a PNG copy when it only takes PNG, otherwise the clipboard (still stickers as a PNG
 * copy, since pasting WebP fails in some apps). Never changes the stored file. [markUsed] runs only after a
 * successful commit.
 */
class StickerInserter(
    private val context: Context,
    private val originalOf: (Sticker) -> File?,
    private val markUsed: suspend (Long) -> Unit,
) {
    private val authority = "${context.packageName}.fileprovider"
    private val sendDir = File(context.cacheDir, SEND_DIR)
    private val label = context.getString(R.string.cd_sticker)

    /** Call on the main thread. [connection] and [editor] are the current field (null when none). */
    suspend fun insert(connection: InputConnection?, editor: EditorInfo?, sticker: Sticker): InsertOutcome {
        val password = editor != null && isPasswordField(editor.inputType)
        val accepted = if (connection != null && editor != null) {
            EditorInfoCompat.getContentMimeTypes(editor).toList()
        } else {
            emptyList()
        }
        val route = planInsert(sticker.mimeType, sticker.isAnimated, accepted, password)
        if (route == InsertRoute.BLOCKED) return InsertOutcome.BLOCKED

        withContext(Dispatchers.IO) { clearSendFolder() }
        val stored = originalOf(sticker)?.takeIf { withContext(Dispatchers.IO) { it.isFile } }
            ?: return InsertOutcome.FAILED
        val storedUri = uriOf(stored) ?: return InsertOutcome.FAILED

        when (route) {
            InsertRoute.COMMIT -> {
                if (commit(connection!!, editor!!, storedUri, sticker.mimeType)) return sent(sticker)
            }
            InsertRoute.COMMIT_AS_PNG -> {
                val png = withContext(Dispatchers.IO) { pngCopyOf(stored, "${sticker.hash}.png") }
                val pngUri = png?.let(::uriOf)
                if (pngUri != null && commit(connection!!, editor!!, pngUri, PNG)) return sent(sticker)
            }
            else -> Unit
        }
        // Not committed: offer it on the clipboard instead. Never claim it was sent.
        val clipPng = if (!sticker.isAnimated && sticker.mimeType != PNG) {
            withContext(Dispatchers.IO) { clipPngOf(stored, sticker.hash) }?.let(::uriOf)
        } else {
            null
        }
        // If the copy can't be made (too big, undecodable), the stored file still goes on the clipboard.
        val copied = if (clipPng != null) copy(clipPng, PNG) else copy(storedUri, sticker.mimeType)
        if (!copied) return InsertOutcome.FAILED
        return if (route == InsertRoute.CLIPBOARD_TYPE_REJECTED) {
            InsertOutcome.COPIED_TYPE_REJECTED
        } else {
            InsertOutcome.COPIED
        }
    }

    private suspend fun sent(sticker: Sticker): InsertOutcome {
        markUsed(sticker.id)
        return InsertOutcome.SENT
    }

    private fun commit(connection: InputConnection, editor: EditorInfo, uri: Uri, mimeType: String): Boolean {
        val info = InputContentInfoCompat(uri, ClipDescription(label, arrayOf(mimeType)), null)
        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        return runCatching { InputConnectionCompat.commitContent(connection, editor, info, flags, null) }
            .getOrDefault(false)
    }

    /** The last clip put on the clipboard. Tests read it: the emulator does not let them read it back. */
    @VisibleForTesting
    var lastCopied: ClipData? = null
        private set

    private fun copy(uri: Uri, mimeType: String): Boolean = runCatching {
        val clip = ClipData(ClipDescription(label, arrayOf(mimeType)), ClipData.Item(uri))
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        lastCopied = clip
    }.isSuccess

    private fun uriOf(file: File): Uri? = runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull()

    /**
     * The clipboard's PNG copy. Named apart from the send copies so the next send does not delete what the
     * clipboard still points to; the previous clipboard copy is removed here instead.
     */
    private fun clipPngOf(source: File, hash: String): File? {
        sendDir.listFiles()?.filter { it.name.startsWith(CLIP_PREFIX) }?.forEach { it.delete() }
        return pngCopyOf(source, "$CLIP_PREFIX$hash.png")
    }

    /** Lossless PNG named [name] with the same pixels, in `cache/send/`. Null if undecodable or too big. */
    private fun pngCopyOf(source: File, name: String): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        if (bounds.outWidth.toLong() * bounds.outHeight > MAX_CONVERT_PIXELS) return null
        val bitmap = BitmapFactory.decodeFile(
            source.path,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        ) ?: return null
        return try {
            sendDir.mkdirs()
            val out = File(sendDir, name)
            out.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) return null }
            out
        } finally {
            bitmap.recycle()
        }
    }

    /** Send copies live until the next send; the clipboard copy until the next clipboard copy. */
    private fun clearSendFolder() {
        sendDir.listFiles()?.filterNot { it.name.startsWith(CLIP_PREFIX) }?.forEach { it.delete() }
    }

    private companion object {
        const val SEND_DIR = "send"
        const val CLIP_PREFIX = "clip-"
        const val PNG = "image/png"

        /** About 64 MB of pixels; bigger stills are copied to the clipboard instead of converted. */
        const val MAX_CONVERT_PIXELS = 4096L * 4096L
    }
}
