package app.kisscut.keys.common

import app.kisscut.keys.shared.core.CollectionRepository
import app.kisscut.keys.shared.core.CollectionSummary
import app.kisscut.keys.shared.core.DeleteMode
import app.kisscut.keys.shared.core.StickerRepository
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerCollection
import kotlinx.coroutines.flow.Flow

/** What the Library, Collections and Inside-a-collection screens need; faked in tests. */
interface LibraryBackend {
    fun observeAll(): Flow<List<Sticker>>
    fun observeFavorites(): Flow<List<Sticker>>
    fun observeRecents(): Flow<List<Sticker>>
    fun observeUnsorted(): Flow<List<Sticker>>

    /** Directly in [collectionId]. */
    fun observeInCollection(collectionId: Long): Flow<List<Sticker>>

    /** In [collectionId] and below. */
    fun observeInTree(collectionId: Long): Flow<List<Sticker>>

    fun observeCollections(): Flow<List<StickerCollection>>
    fun observeSummaries(): Flow<Map<Long, CollectionSummary>>

    suspend fun moveStickers(ids: List<Long>, collectionId: Long?)
    suspend fun setFavorite(ids: List<Long>, value: Boolean)
    suspend fun deleteStickers(ids: List<Long>)

    suspend fun createCollection(name: String, parentId: Long?): StickerCollection
    suspend fun renameCollection(id: Long, name: String)
    suspend fun moveCollection(id: Long, newParentId: Long?)
    suspend fun deleteCollection(id: Long, mode: DeleteMode)
}

class AppLibraryBackend(
    private val stickers: StickerRepository,
    private val collections: CollectionRepository,
) : LibraryBackend {
    override fun observeAll() = stickers.observeAll()
    override fun observeFavorites() = stickers.observeFavorites()
    override fun observeRecents() = stickers.observeRecents()
    override fun observeUnsorted() = stickers.observeUnsorted()
    override fun observeInCollection(collectionId: Long) = stickers.observeInCollection(collectionId)
    override fun observeInTree(collectionId: Long) = stickers.observeInTree(collectionId)
    override fun observeCollections() = collections.observeAll()
    override fun observeSummaries() = collections.observeSummaries()

    override suspend fun moveStickers(ids: List<Long>, collectionId: Long?) = stickers.moveTo(ids, collectionId)
    override suspend fun setFavorite(ids: List<Long>, value: Boolean) = stickers.setFavorite(ids, value)
    override suspend fun deleteStickers(ids: List<Long>) {
        stickers.delete(ids)
    }

    override suspend fun createCollection(name: String, parentId: Long?) = collections.create(name, parentId)
    override suspend fun renameCollection(id: Long, name: String) = collections.rename(id, name)
    override suspend fun moveCollection(id: Long, newParentId: Long?) = collections.move(id, newParentId)
    override suspend fun deleteCollection(id: Long, mode: DeleteMode) {
        collections.delete(id, mode)
    }
}
