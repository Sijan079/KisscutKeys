package com.example.stickers.shared.core

import com.example.stickers.shared.db.Sticker
import com.example.stickers.shared.db.StickerDatabase
import com.example.stickers.shared.db.inTransaction
import kotlinx.coroutines.flow.Flow

/** Result of [StickerRepository.add]: the stored sticker and whether it was newly created. */
data class AddResult(val sticker: Sticker, val isNew: Boolean)

/** Lists are newest first; recents are most recently used first. */
class StickerRepository(
    private val db: StickerDatabase,
    private val files: FileStore,
    private val now: () -> Long,
) {
    private val stickers = db.stickerDao()

    fun observeAll(): Flow<List<Sticker>> = stickers.observeAll()
    fun observeInCollection(collectionId: Long): Flow<List<Sticker>> = stickers.observeInCollection(collectionId)
    /** Stickers in [collectionId] and every collection below it. */
    fun observeInTree(collectionId: Long): Flow<List<Sticker>> = stickers.observeInTree(collectionId)
    fun observeUnsorted(): Flow<List<Sticker>> = stickers.observeUnsorted()
    fun observeFavorites(): Flow<List<Sticker>> = stickers.observeFavorites()
    fun observeRecents(limit: Int = DEFAULT_RECENTS): Flow<List<Sticker>> = stickers.observeRecents(limit)

    /**
     * Adds a sticker whose files are already stored. If one with the same hash exists, nothing is
     * inserted and the existing sticker is returned; with [moveIfExisting] it is also moved to
     * [collectionId].
     */
    suspend fun add(new: NewSticker, collectionId: Long?, moveIfExisting: Boolean = false): AddResult {
        require(new.hash.isNotBlank()) { "hash is blank" }
        require(new.filePath.isNotBlank() && new.thumbPath.isNotBlank()) { "file paths are blank" }
        require(new.mimeType in SUPPORTED_MIME_TYPES) { "unsupported type ${new.mimeType}" }

        return db.inTransaction {
            val row = Sticker(
                filePath = new.filePath,
                thumbPath = new.thumbPath,
                mimeType = new.mimeType,
                isAnimated = new.isAnimated,
                hasAlpha = new.hasAlpha,
                hash = new.hash,
                collectionId = collectionId,
                addedAt = now(),
            )
            val id = stickers.insert(row)
            if (id != -1L) {
                AddResult(row.copy(id = id), isNew = true)
            } else {
                var existing = checkNotNull(stickers.findByHash(new.hash))
                if (moveIfExisting && existing.collectionId != collectionId) {
                    stickers.moveTo(listOf(existing.id), collectionId)
                    existing = existing.copy(collectionId = collectionId)
                }
                AddResult(existing, isNew = false)
            }
        }
    }

    suspend fun findByHash(hash: String): Sticker? = stickers.findByHash(hash)

    suspend fun moveTo(ids: List<Long>, collectionId: Long?) = stickers.moveTo(ids, collectionId)

    suspend fun setFavorite(ids: List<Long>, value: Boolean) = stickers.setFavorite(ids, value)

    /** Call after a successful insert into a chat app, not on tap. */
    suspend fun markUsed(id: Long) = stickers.markUsed(id, now())

    /** Deletes the rows, then their files. */
    suspend fun delete(ids: List<Long>): FileDeleteReport {
        val doomed = db.inTransaction {
            stickers.getByIds(ids).also { stickers.delete(ids) }
        }
        return files.deleteFilesOf(doomed)
    }

    /** One-shot list for export. */
    suspend fun allOnce(): List<Sticker> = stickers.allOnce()

    companion object {
        const val DEFAULT_RECENTS = 30
        val SUPPORTED_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
    }
}
