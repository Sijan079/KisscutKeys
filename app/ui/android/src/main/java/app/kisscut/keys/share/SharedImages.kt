package app.kisscut.keys.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Image URIs from an `ACTION_SEND` / `ACTION_SEND_MULTIPLE` share, in order and without repeats.
 * Anything that is not a `content:` URI is dropped: `file:` URIs from other apps are not read.
 * The declared MIME type is not trusted; the importer sniffs the bytes.
 */
fun Intent.sharedImageUris(): List<Uri> {
    val type = type
    if (type != null && !type.startsWith("image/")) return emptyList()
    val uris = when (action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
    val fromClip = clipData?.uris().orEmpty()
    return (uris + fromClip).filter { it.scheme == "content" }.distinct()
}

private fun ClipData.uris(): List<Uri> = (0 until itemCount).mapNotNull { getItemAt(it).uri }
