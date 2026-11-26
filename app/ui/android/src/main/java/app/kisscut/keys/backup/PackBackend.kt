package app.kisscut.keys.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.kisscut.keys.shared.core.pack.ExportResult
import app.kisscut.keys.shared.core.pack.ExportScope
import app.kisscut.keys.shared.core.pack.ImportSummary
import app.kisscut.keys.shared.core.pack.PackExporter
import app.kisscut.keys.shared.core.pack.PackImporter
import app.kisscut.keys.shared.core.pack.PackSettings
import app.kisscut.keys.shared.core.pack.PackZipReader
import app.kisscut.keys.shared.core.pack.PackZipWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

typealias Progress = (done: Int, total: Int) -> Unit

/** Backup and sharing for the Settings screen (docs/IMPORT_EXPORT.md). Uris are strings so tests need no Android. */
interface PackBackend {
    /** Writes the whole library, with [settings], to the document the user picked. */
    suspend fun backUp(uri: String, settings: PackSettings, onProgress: Progress): ExportResult

    /** Writes one collection to a temporary file and returns a content uri to share it. */
    suspend fun exportForShare(collectionId: Long, name: String, onProgress: Progress): String

    suspend fun import(uri: String, onProgress: Progress): ImportSummary
}

/**
 * [PackBackend] over the Storage Access Framework and a `cache/export/` folder shared through the
 * FileProvider. A failed or cancelled export removes what it wrote.
 */
class AndroidPackBackend(
    context: Context,
    private val exporter: PackExporter,
    private val importer: PackImporter,
    /** Where the library's files live, for the free-space check. */
    private val filesDir: File = context.filesDir,
) : PackBackend {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val exportDir = File(appContext.cacheDir, EXPORT_DIR)

    override suspend fun backUp(uri: String, settings: PackSettings, onProgress: Progress): ExportResult {
        val target = uri.toUri()
        return try {
            withContext(Dispatchers.IO) {
                val out = resolver.openOutputStream(target, "wt") ?: throw FileNotFoundException(uri)
                PackZipWriter(out).use { exporter.export(ExportScope.All, it, settings, onProgress) }
            }
        } catch (e: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(resolver, target) }
            }
            throw e
        }
    }

    override suspend fun exportForShare(collectionId: Long, name: String, onProgress: Progress): String {
        val file = withContext(Dispatchers.IO) {
            // One shared pack at a time: the previous one has been handed to the share sheet already.
            exportDir.deleteRecursively()
            exportDir.mkdirs()
            File(exportDir, fileName(name))
        }
        try {
            withContext(Dispatchers.IO) {
                file.outputStream().use { out ->
                    PackZipWriter(out).use { writer ->
                        exporter.export(ExportScope.Collection(collectionId), writer, null, onProgress)
                    }
                }
            }
        } catch (e: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { file.delete() }
            throw e
        }
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file).toString()
    }

    override suspend fun import(uri: String, onProgress: Progress): ImportSummary {
        val source = uri.toUri()
        val size = withContext(Dispatchers.IO) { sizeOf(source) }
        val reader = PackZipReader({ resolver.openInputStream(source) ?: throw FileNotFoundException(uri) })
        return importer.import(reader, packSize = size, freeSpace = filesDir.usableSpace, onProgress = onProgress)
    }

    private fun sizeOf(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    companion object {
        const val EXPORT_DIR = "export"
        const val EXTENSION = ".stickerpack"

        /** A file name from a collection name: letters, digits, dashes; never empty. */
        fun fileName(name: String): String {
            val clean = name.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(60)
            return clean.ifEmpty { "stickers" } + EXTENSION
        }
    }
}
