package app.kisscut.keys.shared.core.pack

import app.kisscut.keys.shared.core.PrepareResult
import app.kisscut.keys.shared.core.PreparedSticker
import app.kisscut.keys.shared.core.RejectReason
import app.kisscut.keys.shared.core.StickerImporter
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerCollection
import app.kisscut.keys.shared.db.StickerDatabase
import app.kisscut.keys.shared.db.inTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Where a pack is read from: a zip on Android and the JVM ([PackZipReader]). */
interface PackSource {
    /** Finds and checks the manifest; throws [PackException]. */
    fun readManifest(): PackManifest

    /** Streams the `stickers/` entries (bytes null when over the size cap); returns `settings.json`. */
    suspend fun readEntries(onSticker: suspend (path: String, bytes: ByteArray?) -> Unit): ByteArray?
}

/** Storing a sticker failed during a pack import (disk full, I/O error); the import was undone. */
class PackWriteException : Exception("could not store a sticker; the import was undone")

/** What an import did (docs/IMPORT_EXPORT.md, Import step 5). */
data class ImportSummary(
    val added: Int,
    /** Already in the library, or twice in the pack; left as they were. */
    val duplicates: Int,
    /** Skipped stickers by reason. */
    val rejected: Map<RejectReason, Int>,
    /** Listed in the manifest but not in the zip. */
    val missing: Int,
    val collectionsCreated: Int,
    /** Same name under the same parent as an existing collection, so merged into it. */
    val collectionsMerged: Int,
    /** Deeper than the cap, so folded into an ancestor. */
    val collectionsFlattened: Int,
    /** The pack's `settings.json`, when it had a readable one. */
    val settings: PackSettings?,
) {
    val rejectedTotal: Int get() = rejected.values.sum() + missing
}

/**
 * Imports a `.stickerpack` all or nothing (docs/IMPORT_EXPORT.md, Import). Sticker files are converted and
 * written first; collections and rows are added in one short transaction at the end, so the keyboard (which
 * shares the database) never waits on a long import. On failure or cancel, nothing is added and every file
 * this import wrote is removed.
 */
class PackImporter(
    private val db: StickerDatabase,
    private val importer: StickerImporter,
    private val now: () -> Long,
    private val limits: PackLimits = PackLimits(),
) {
    /**
     * [packSize] and [freeSpace] are checked first when known (a pack must fit, plus
     * [PackLimits.freeSpaceMargin]). New top-level collections go to the root of the library.
     */
    suspend fun import(
        source: PackSource,
        packSize: Long? = null,
        freeSpace: Long? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportSummary = withContext(Dispatchers.IO) {
        if (packSize != null && packSize > limits.maxPackBytes) throw PackException(PackProblem.OverLimit("pack size"))
        if (packSize != null && freeSpace != null && freeSpace < packSize + limits.freeSpaceMargin) {
            throw PackException(PackProblem.NotEnoughSpace)
        }
        val manifest = source.readManifest()
        val plan = planCollections(manifest.collections)
        val byPath = LinkedHashMap<String, PackSticker>()
        manifest.stickers.forEach { byPath.getOrPut(it.file) { it } }
        val total = manifest.stickers.size
        var duplicates = total - byPath.size // the same file listed twice
        val rejected = mutableMapOf<RejectReason, Int>()
        val ready = mutableListOf<Pair<PreparedSticker, PackSticker>>()
        val hashes = mutableSetOf<String>()
        val seen = mutableSetOf<String>()

        try {
            val settingsBytes = source.readEntries { path, bytes ->
                val entry = byPath[path] ?: return@readEntries // not in the manifest: ignored
                if (!seen.add(path)) return@readEntries
                when (val r = if (bytes == null) null else importer.prepare(bytes, entry.hash)) {
                    null -> rejected.count(RejectReason.TOO_LARGE)
                    // A failed write (disk full) is not one bad sticker: stop and undo everything.
                    is PrepareResult.Rejected ->
                        if (r.reason == RejectReason.FAILED) throw PackWriteException() else rejected.count(r.reason)
                    is PrepareResult.Duplicate -> duplicates++
                    is PrepareResult.Ready ->
                        if (hashes.add(r.prepared.new.hash)) {
                            ready += r.prepared to entry
                        } else {
                            duplicates++ // same bytes twice in this pack; the files are shared
                        }
                }
                onProgress(seen.size, byPath.size)
            }
            val counts = commit(plan, ready)
            ImportSummary(
                added = counts.added,
                duplicates = duplicates + counts.raced,
                rejected = rejected,
                missing = byPath.size - seen.size,
                collectionsCreated = counts.created,
                collectionsMerged = counts.merged,
                collectionsFlattened = plan.flattened,
                settings = settingsBytes?.let(::parseSettings),
            )
        } catch (e: Throwable) {
            withContext(NonCancellable) { ready.forEach { (prepared, _) -> importer.discard(prepared) } }
            throw e
        }
    }

    private class Counts(var added: Int = 0, var raced: Int = 0, var created: Int = 0, var merged: Int = 0)

    /** Adds the collections (merging same names) and the rows, in one transaction. */
    private suspend fun commit(plan: CollectionPlan, ready: List<Pair<PreparedSticker, PackSticker>>): Counts {
        val counts = Counts()
        val collections = db.collectionDao()
        val stickers = db.stickerDao()
        db.inTransaction {
            val idOf = HashMap<Long, Long>()
            for (c in plan.ordered) {
                val parent = c.parentPackId?.let(idOf::getValue)
                val existing = collections.childrenOnce(parent)
                    .firstOrNull { it.name.trim().equals(c.name, ignoreCase = true) }
                idOf[c.packId] = if (existing != null) {
                    counts.merged++
                    existing.id
                } else {
                    counts.created++
                    val order = (collections.maxSortOrder(parent) ?: -1) + 1
                    val row = StickerCollection(name = c.name, parentId = parent, sortOrder = order, createdAt = now())
                    collections.insert(row)
                }
            }
            for ((prepared, entry) in ready) {
                val new = prepared.new
                val row = Sticker(
                    filePath = new.filePath,
                    thumbPath = new.thumbPath,
                    mimeType = new.mimeType,
                    isAnimated = new.isAnimated,
                    hasAlpha = new.hasAlpha,
                    hash = new.hash,
                    collectionId = plan.targetOf(entry.collectionId)?.let(idOf::getValue),
                    isFavorite = entry.isFavorite,
                    addedAt = now(),
                )
                // -1: the same hash was saved meanwhile (a share during the import); its files are the same.
                if (stickers.insert(row) == -1L) counts.raced++ else counts.added++
            }
        }
        return counts
    }

    private fun MutableMap<RejectReason, Int>.count(reason: RejectReason) {
        this[reason] = (this[reason] ?: 0) + 1
    }

    private fun parseSettings(bytes: ByteArray): PackSettings? = runCatching {
        PackFormat.json.decodeFromString(PackSettings.serializer(), bytes.decodeToString())
    }.getOrNull()
}
