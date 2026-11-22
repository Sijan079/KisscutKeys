package app.kisscut.keys.shared.core

import app.kisscut.keys.shared.db.CollectionWithCount
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerCollection

/** What the Collections list shows for one collection. Counts include descendants where named "total". */
data class CollectionSummary(
    val collection: StickerCollection,
    /** Top level is 1. */
    val depth: Int,
    val directCount: Int,
    val totalCount: Int,
    /** Direct subcollections. */
    val childCount: Int,
    /** Up to [PREVIEW_COUNT] stickers: its own newest first, then from descendants. */
    val previews: List<Sticker>,
)

const val PREVIEW_COUNT = 3

/**
 * Builds a summary per collection id from [all] collections with their direct counts and [previews]
 * (the newest few stickers of each collection, see `StickerRepository.observePreviews`).
 */
fun summarizeCollections(
    all: List<CollectionWithCount>,
    previews: List<Sticker>,
): Map<Long, CollectionSummary> {
    val children = all.groupBy { it.collection.parentId }
        .mapValues { (_, list) -> list.sortedWith(compareBy({ it.collection.sortOrder }, { it.collection.id })) }
    val ownPreviews = previews.groupBy { it.collectionId }
        .mapValues { (_, list) -> list.sortedWith(compareByDescending<Sticker> { it.addedAt }.thenByDescending { it.id }) }
    val result = mutableMapOf<Long, CollectionSummary>()

    fun visit(node: CollectionWithCount, depth: Int, seen: Set<Long>): CollectionSummary {
        val id = node.collection.id
        val kids = children[id].orEmpty().filter { it.collection.id !in seen }
        val kidSummaries = kids.map { visit(it, depth + 1, seen + id) }
        val shown = (ownPreviews[id].orEmpty() + kidSummaries.flatMap { it.previews }).take(PREVIEW_COUNT)
        return CollectionSummary(
            collection = node.collection,
            depth = depth,
            directCount = node.stickerCount,
            totalCount = node.stickerCount + kidSummaries.sumOf { it.totalCount },
            childCount = kids.size,
            previews = shown,
        ).also { result[id] = it }
    }
    children[null].orEmpty().forEach { visit(it, 1, emptySet()) }
    return result
}

enum class HeroReason { MOST_USED, NEWEST }

data class Hero(val sticker: Sticker, val reason: HeroReason)

/** The big Library tile: the most used sticker, or the newest when none has been used yet. */
fun pickHero(stickers: List<Sticker>): Hero? {
    val used = stickers.filter { it.useCount > 0 }
    if (used.isNotEmpty()) {
        val top = used.maxWith(compareBy<Sticker> { it.useCount }.thenBy { it.lastUsedAt ?: 0 }.thenBy { it.id })
        return Hero(top, HeroReason.MOST_USED)
    }
    val newest = stickers.maxWithOrNull(compareBy<Sticker> { it.addedAt }.thenBy { it.id }) ?: return null
    return Hero(newest, HeroReason.NEWEST)
}

enum class StickerKind { ALL, STATIC, ANIMATED }

fun List<Sticker>.filterKind(kind: StickerKind): List<Sticker> = when (kind) {
    StickerKind.ALL -> this
    StickerKind.STATIC -> filter { !it.isAnimated }
    StickerKind.ANIMATED -> filter { it.isAnimated }
}

/** Select mode "Favorite": favorite all, unless every selected sticker already is one (then unfavorite). */
fun favoriteActionValue(selected: List<Sticker>): Boolean = selected.isEmpty() || !selected.all { it.isFavorite }

/**
 * Whether [id] may move under [newParentId] (null = top level): same rules as CollectionRepository.move
 * (no cycles, depth cap), plus "not its current parent", so the UI only offers real moves.
 */
fun canMoveCollection(all: List<StickerCollection>, id: Long, newParentId: Long?): Boolean {
    val byId = all.associateBy { it.id }
    val moving = byId[id] ?: return false
    if (moving.parentId == newParentId) return false
    if (newParentId == null) return true
    if (newParentId !in byId) return false
    val children = all.groupBy { it.parentId }
    val subtree = mutableSetOf<Long>()
    fun height(node: Long): Int {
        if (!subtree.add(node)) return 0
        return 1 + (children[node].orEmpty().maxOfOrNull { height(it.id) } ?: 0)
    }
    val h = height(id)
    if (newParentId in subtree) return false
    val parentDepth = collectionAncestors(all, newParentId).size + 1
    return parentDepth + h <= CollectionRepository.MAX_DEPTH
}
