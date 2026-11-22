package app.kisscut.keys.shared.core.pack

import app.kisscut.keys.shared.core.FileArea
import app.kisscut.keys.shared.core.FileStore
import app.kisscut.keys.shared.db.StickerCollection
import app.kisscut.keys.shared.db.StickerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Where a pack is written: a zip on Android and the JVM ([PackZipWriter]). Calls block. */
interface PackSink {
    fun putManifest(manifest: PackManifest)
    fun putSettings(settings: PackSettings)
    fun putSticker(path: String, bytes: ByteArray)
}

sealed interface ExportScope {
    data object All : ExportScope

    /** One collection with everything below it; it becomes the pack's top level. */
    data class Collection(val id: Long) : ExportScope
}

data class ExportResult(val stickers: Int, val collections: Int, val bytes: Long)

/**
 * Writes the library, or one collection, as a `.stickerpack` (docs/IMPORT_EXPORT.md, Export): manifest
 * first, then one sticker file at a time. Never writes a pack the importer would refuse: over
 * [PackLimits.maxStickers] or [PackLimits.maxPackBytes] it throws [PackException] and the caller deletes
 * the partial file. Usage history is never exported.
 */
class PackExporter(
    private val db: StickerDatabase,
    private val files: FileStore,
    private val now: () -> Long,
    private val appVersion: String,
    private val limits: PackLimits = PackLimits(),
) {
    suspend fun export(
        scope: ExportScope,
        sink: PackSink,
        settings: PackSettings? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportResult = withContext(Dispatchers.IO) {
        val allCollections = db.collectionDao().observeAll().first()
        val inScope: Set<Long>? = when (scope) {
            ExportScope.All -> null
            is ExportScope.Collection -> {
                require(allCollections.any { it.id == scope.id }) { "no collection ${scope.id}" }
                subtree(scope.id, allCollections.groupBy { it.parentId })
            }
        }
        val collections = allCollections
            .filter { inScope == null || it.id in inScope }
            .map { c ->
                val parent = if (scope is ExportScope.Collection && c.id == scope.id) null else c.parentId
                PackCollection(c.id, c.name, parent, c.sortOrder)
            }
        val stickers = db.stickerDao().allOnce()
            .filter { inScope == null || it.collectionId in inScope }
            // A sticker whose file is gone cannot be packed; leave it out rather than break the pack.
            .filter { files.exists(FileArea.STICKERS, it.filePath) }
        if (stickers.size > limits.maxStickers) throw PackException(PackProblem.OverLimit("stickers"))

        val entries = stickers.map { s ->
            PackSticker(
                file = PackFormat.STICKER_DIR + s.hash + "." + s.filePath.substringAfterLast('.'),
                hash = s.hash,
                mimeType = s.mimeType,
                collectionId = s.collectionId,
                isFavorite = s.isFavorite,
            )
        }
        val manifest = PackManifest(
            format = PackFormat.FORMAT,
            version = PackFormat.VERSION,
            createdAt = now(),
            appVersion = appVersion,
            collections = collections,
            stickers = entries,
        )
        val manifestBytes = encodeManifest(manifest)
        if (manifestBytes.size > limits.maxManifestBytes) throw PackException(PackProblem.OverLimit("manifest"))
        sink.putManifest(manifest)
        settings?.let(sink::putSettings)

        var bytes = manifestBytes.size.toLong()
        stickers.forEachIndexed { i, s ->
            currentCoroutineContext().ensureActive()
            val data = files.read(FileArea.STICKERS, s.filePath)
                ?: throw PackException(PackProblem.Malformed) // deleted while exporting
            bytes += data.size
            if (bytes > limits.maxPackBytes) throw PackException(PackProblem.OverLimit("pack size"))
            sink.putSticker(entries[i].file, data)
            onProgress(i + 1, stickers.size)
        }
        ExportResult(stickers.size, collections.size, bytes)
    }

    private fun subtree(root: Long, children: Map<Long?, List<StickerCollection>>): Set<Long> {
        val out = mutableSetOf<Long>()
        val stack = ArrayDeque(listOf(root))
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (out.add(id)) children[id].orEmpty().forEach { stack += it.id }
        }
        return out
    }
}
