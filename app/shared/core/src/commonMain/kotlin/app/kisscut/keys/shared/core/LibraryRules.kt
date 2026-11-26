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
        .mapValues { (_, list) ->
            list.sortedWith(compareByDescending<Sticker> { it.addedAt }.thenByDescending { it.id })
        }
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

/** Most used first: more uses, then used more recently, then higher id. */
private val MOST_USED_FIRST =
    compareByDescending<Sticker> { it.useCount }.thenByDescending { it.lastUsedAt ?: 0 }.thenByDescending { it.id }

/** The big Library tile: the most used sticker, or the newest when none has been used yet. */
fun pickHero(stickers: List<Sticker>): Hero? {
    val top = stickers.filter { it.useCount > 0 }.minWithOrNull(MOST_USED_FIRST)
    if (top != null) return Hero(top, HeroReason.MOST_USED)
    val newest = stickers.maxWithOrNull(compareBy<Sticker> { it.addedAt }.thenBy { it.id }) ?: return null
    return Hero(newest, HeroReason.NEWEST)
}

/**
 * The Library's All view (docs/UI_SPEC.md, Library sections). A sticker is in at most one section; the
 * "All stickers" grid below still shows every sticker.
 */
data class LibrarySections(
    /** First is the big tile (same as [pickHero]); the rest sit beside it. Empty until something is used. */
    val mostUsed: List<Sticker>,
    /** Newest first. */
    val new: List<Sticker>,
    /** A random pick from the stickers with the fewest uses (never used ones first in practice). */
    val leastUsed: List<Sticker>,
) {
    companion object {
        const val MOST_USED_COUNT = 3
        const val NEW_COUNT = 6
        const val LEAST_USED_COUNT = 6

        /** Below this many stickers the sections would mostly repeat the grid, so they are not shown. */
        const val MIN_STICKERS = MOST_USED_COUNT + NEW_COUNT + LEAST_USED_COUNT + 1
    }
}

/**
 * Splits [stickers] into sections, or null when there are fewer than [LibrarySections.MIN_STICKERS].
 *
 * Least used: the pool is every sticker left whose use count is at most the count of the
 * [LibrarySections.LEAST_USED_COUNT]-th least used one, ordered by a hash of [seed] and the id. One
 * [seed] per app session keeps the pick stable while stickers are used or added (only the tile that
 * changed moves), and a new session shows a new pick.
 */
fun librarySections(stickers: List<Sticker>, seed: Long): LibrarySections? {
    if (stickers.size < LibrarySections.MIN_STICKERS) return null
    val mostUsed = stickers.filter { it.useCount > 0 }.sortedWith(MOST_USED_FIRST).take(LibrarySections.MOST_USED_COUNT)
    val taken = mostUsed.mapTo(HashSet()) { it.id }
    val new = stickers.filter { it.id !in taken }
        .sortedWith(compareByDescending<Sticker> { it.addedAt }.thenByDescending { it.id })
        .take(LibrarySections.NEW_COUNT)
    new.mapTo(taken) { it.id }
    val rest = stickers.filter { it.id !in taken }
    val cutoff = rest.map { it.useCount }.sorted()
        .let { it.getOrNull(LibrarySections.LEAST_USED_COUNT - 1) ?: it.lastOrNull() }
    val leastUsed = rest.filter { cutoff != null && it.useCount <= cutoff }
        .sortedWith(compareBy<Sticker> { mix(it.id xor seed) }.thenBy { it.id })
        .take(LibrarySections.LEAST_USED_COUNT)
    return LibrarySections(mostUsed, new, leastUsed)
}

/** SplitMix64 finalizer: spreads ids so neighbouring ids land far apart in the order. */
private fun mix(x: Long): Long {
    var z = x + -0x61c8864680b583ebL
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
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
