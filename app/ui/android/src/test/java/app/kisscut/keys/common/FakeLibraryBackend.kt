package app.kisscut.keys.common

import app.kisscut.keys.shared.core.CollectionRepository
import app.kisscut.keys.shared.core.CollectionRuleException
import app.kisscut.keys.shared.core.CollectionSummary
import app.kisscut.keys.shared.core.DeleteMode
import app.kisscut.keys.shared.core.PREVIEW_COUNT
import app.kisscut.keys.shared.core.canMoveCollection
import app.kisscut.keys.shared.core.collectionAncestors
import app.kisscut.keys.shared.core.summarizeCollections
import app.kisscut.keys.shared.db.CollectionWithCount
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory library with the same rules as the repositories (depth cap, cycles, delete modes). */
class FakeLibraryBackend : LibraryBackend {
    val stickers = MutableStateFlow<List<Sticker>>(emptyList())
    val collections = MutableStateFlow<List<StickerCollection>>(emptyList())
    private var nextId = 1000L

    fun addCollection(id: Long, name: String, parentId: Long?, sortOrder: Int = id.toInt()) {
        collections.value = collections.value + StickerCollection(id, name, parentId, sortOrder, 0)
    }

    fun addSticker(
        id: Long,
        collectionId: Long? = null,
        addedAt: Long = id,
        favorite: Boolean = false,
        animated: Boolean = false,
        useCount: Int = 0,
        lastUsedAt: Long? = null,
    ) {
        stickers.value = stickers.value + Sticker(
            id = id, filePath = "$id.png", thumbPath = "$id.webp", mimeType = "image/png",
            isAnimated = animated, hasAlpha = true, hash = "h$id", collectionId = collectionId,
            isFavorite = favorite, useCount = useCount, lastUsedAt = lastUsedAt, addedAt = addedAt,
        )
    }

    private fun Flow<List<Sticker>>.newestFirst() =
        map { list -> list.sortedWith(compareByDescending<Sticker> { it.addedAt }.thenByDescending { it.id }) }

    override fun observeAll() = stickers.newestFirst()
    override fun observeFavorites() = stickers.map { l -> l.filter { it.isFavorite } }.newestFirst()
    override fun observeRecents() = stickers.map { l ->
        l.filter { it.lastUsedAt != null }.sortedByDescending { it.lastUsedAt }
    }
    override fun observeUnsorted() = stickers.map { l -> l.filter { it.collectionId == null } }.newestFirst()
    override fun observeInCollection(collectionId: Long) =
        stickers.map { l -> l.filter { it.collectionId == collectionId } }.newestFirst()

    override fun observeInTree(collectionId: Long) = combine(stickers, collections) { l, all ->
        val ids = subtree(all, collectionId)
        l.filter { it.collectionId in ids }
    }.newestFirst()

    override fun observeCollections() = collections.map { l -> l.sortedWith(compareBy({ it.sortOrder }, { it.id })) }

    override fun observeSummaries(): Flow<Map<Long, CollectionSummary>> = combine(stickers, collections) { l, all ->
        val counts = all.map { c -> CollectionWithCount(c, l.count { it.collectionId == c.id }) }
        val previews = l.groupBy { it.collectionId }.filterKeys { it != null }.values.flatMap { group ->
            group.sortedWith(compareByDescending<Sticker> { it.addedAt }.thenByDescending { it.id }).take(PREVIEW_COUNT)
        }
        summarizeCollections(counts, previews)
    }

    override suspend fun moveStickers(ids: List<Long>, collectionId: Long?) {
        stickers.value = stickers.value.map { if (it.id in ids) it.copy(collectionId = collectionId) else it }
    }

    override suspend fun setFavorite(ids: List<Long>, value: Boolean) {
        stickers.value = stickers.value.map { if (it.id in ids) it.copy(isFavorite = value) else it }
    }

    override suspend fun deleteStickers(ids: List<Long>) {
        stickers.value = stickers.value.filter { it.id !in ids }
    }

    override suspend fun createCollection(name: String, parentId: Long?): StickerCollection {
        val clean = name.trim().ifEmpty { throw CollectionRuleException.BlankName() }
        val all = collections.value
        if (parentId != null) {
            if (all.none { it.id == parentId }) throw CollectionRuleException.NotFound(parentId)
            if (collectionAncestors(all, parentId).size + 2 > CollectionRepository.MAX_DEPTH) {
                throw CollectionRuleException.TooDeep()
            }
        }
        val c = StickerCollection(nextId++, clean, parentId, all.size, 0)
        collections.value = all + c
        return c
    }

    override suspend fun renameCollection(id: Long, name: String) {
        val clean = name.trim().ifEmpty { throw CollectionRuleException.BlankName() }
        collections.value = collections.value.map { if (it.id == id) it.copy(name = clean) else it }
    }

    override suspend fun moveCollection(id: Long, newParentId: Long?) {
        if (!canMoveCollection(collections.value, id, newParentId)) throw CollectionRuleException.TooDeep()
        collections.value = collections.value.map { if (it.id == id) it.copy(parentId = newParentId) else it }
    }

    override suspend fun deleteCollection(id: Long, mode: DeleteMode) {
        val all = collections.value
        val target = all.first { it.id == id }
        when (mode) {
            DeleteMode.COLLECTION_ONLY -> {
                collections.value = all.filter { it.id != id }
                    .map { if (it.parentId == id) it.copy(parentId = target.parentId) else it }
                moveStickers(stickers.value.filter { it.collectionId == id }.map { it.id }, target.parentId)
            }
            DeleteMode.EVERYTHING -> {
                val ids = subtree(all, id)
                collections.value = all.filter { it.id !in ids }
                stickers.value = stickers.value.filter { it.collectionId !in ids }
            }
        }
    }

    private fun subtree(all: List<StickerCollection>, id: Long): Set<Long> {
        val out = mutableSetOf(id)
        var grew = true
        while (grew) {
            grew = all.filter { it.parentId in out && it.id !in out }.map { it.id }.let { out.addAll(it) }
        }
        return out
    }
}
