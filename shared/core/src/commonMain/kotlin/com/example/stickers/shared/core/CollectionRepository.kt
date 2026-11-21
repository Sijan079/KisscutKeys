package com.example.stickers.shared.core

import com.example.stickers.shared.db.CollectionWithCount
import com.example.stickers.shared.db.StickerCollection
import com.example.stickers.shared.db.StickerDatabase
import com.example.stickers.shared.db.inTransaction
import kotlinx.coroutines.flow.Flow

/** Why a collection change was refused. */
sealed class CollectionRuleException(message: String) : IllegalArgumentException(message) {
    class BlankName : CollectionRuleException("Collection name is blank")
    class NotFound(val id: Long) : CollectionRuleException("Collection $id not found")
    class TooDeep : CollectionRuleException("Collections nest at most ${CollectionRepository.MAX_DEPTH} levels")
    class Cycle : CollectionRuleException("A collection cannot move into itself or its descendants")
}

enum class DeleteMode {
    /** Children move up to the parent, stickers move to the parent (or Unsorted). Nothing is lost. */
    COLLECTION_ONLY,

    /** The collection, all descendants and all their stickers, including files. */
    EVERYTHING,
}

/** Files that could not be deleted (area to relative path). Rows are gone either way. */
data class FileDeleteReport(val failed: List<Pair<FileArea, String>> = emptyList())

class CollectionRepository(
    private val db: StickerDatabase,
    private val files: FileStore,
    private val now: () -> Long,
) {
    private val collections = db.collectionDao()
    private val stickers = db.stickerDao()

    fun observeChildren(parentId: Long?): Flow<List<CollectionWithCount>> = collections.observeChildren(parentId)

    fun observeById(id: Long): Flow<StickerCollection?> = collections.observeById(id)

    /** Stickers in [id] and all its descendants. */
    suspend fun totalStickerCount(id: Long): Int = collections.totalStickerCount(id)

    /** Creates a collection at the end of its siblings. Name is trimmed. */
    suspend fun create(name: String, parentId: Long?): StickerCollection {
        val cleanName = cleanName(name)
        return db.inTransaction {
            val parentDepth = if (parentId == null) 0 else depthOrThrow(parentId)
            if (parentDepth + 1 > MAX_DEPTH) throw CollectionRuleException.TooDeep()
            val row = StickerCollection(
                name = cleanName,
                parentId = parentId,
                sortOrder = nextSortOrder(parentId),
                createdAt = now(),
            )
            row.copy(id = collections.insert(row))
        }
    }

    suspend fun rename(id: Long, name: String) {
        val cleanName = cleanName(name)
        db.inTransaction {
            depthOrThrow(id)
            collections.rename(id, cleanName)
        }
    }

    /** Moves [id] (with its subtree) to the end of [newParentId]'s children. */
    suspend fun move(id: Long, newParentId: Long?) {
        db.inTransaction {
            depthOrThrow(id)
            val parentDepth = if (newParentId == null) {
                0
            } else {
                depthOrThrow(newParentId).also {
                    if (newParentId in collections.descendantIds(id)) throw CollectionRuleException.Cycle()
                }
            }
            if (parentDepth + collections.subtreeHeight(id) > MAX_DEPTH) throw CollectionRuleException.TooDeep()
            collections.move(id, newParentId, nextSortOrder(newParentId))
        }
    }

    /** Sets the order of [parentId]'s children. [orderedIds] must be exactly those children. */
    suspend fun reorder(parentId: Long?, orderedIds: List<Long>) {
        db.inTransaction {
            val siblings = collections.childrenOnce(parentId).map { it.id }
            require(orderedIds.size == siblings.size && orderedIds.toSet() == siblings.toSet()) {
                "reorder needs exactly the current children of $parentId"
            }
            orderedIds.forEachIndexed { index, childId -> collections.setSortOrder(childId, index) }
        }
    }

    /** Deletes a collection; see [DeleteMode]. Files are removed after the rows are committed. */
    suspend fun delete(id: Long, mode: DeleteMode): FileDeleteReport = when (mode) {
        DeleteMode.COLLECTION_ONLY -> {
            db.inTransaction {
                val target = collections.getById(id) ?: throw CollectionRuleException.NotFound(id)
                // Re-parent and move stickers BEFORE deleting the row: the parentId FK cascades.
                val base = nextSortOrder(target.parentId)
                collections.childrenOnce(id).forEachIndexed { i, child ->
                    collections.move(child.id, target.parentId, base + i)
                }
                stickers.moveAllFromCollection(from = id, to = target.parentId)
                collections.deleteRow(id)
            }
            FileDeleteReport()
        }

        DeleteMode.EVERYTHING -> {
            val doomed = db.inTransaction {
                depthOrThrow(id)
                val ids = collections.descendantIds(id)
                // Delete stickers BEFORE collections: collectionId is SET NULL on delete, which would
                // otherwise leave them alive in Unsorted.
                stickers.inCollections(ids).also { list ->
                    stickers.delete(list.map { it.id })
                    collections.deleteRows(ids)
                }
            }
            files.deleteFilesOf(doomed)
        }
    }

    private fun cleanName(name: String): String =
        name.trim().ifEmpty { throw CollectionRuleException.BlankName() }

    /** Depth of an existing collection (top level = 1); throws NotFound when it does not exist. */
    private suspend fun depthOrThrow(id: Long): Int =
        collections.depthOf(id).takeIf { it > 0 } ?: throw CollectionRuleException.NotFound(id)

    private suspend fun nextSortOrder(parentId: Long?): Int = (collections.maxSortOrder(parentId) ?: -1) + 1

    companion object {
        const val MAX_DEPTH = 3
    }
}
