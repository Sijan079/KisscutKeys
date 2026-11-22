package app.kisscut.keys.shared.core

import app.kisscut.keys.shared.db.StickerCollection

/** One visible row of a nested collection picker. [depth] is 1 for top level. */
data class CollectionTreeRow(
    val collection: StickerCollection,
    val depth: Int,
    val hasChildren: Boolean,
    val isExpanded: Boolean,
) {
    /** Whether a new collection may be created inside this one (depth cap). */
    val canHaveChildren: Boolean get() = depth < CollectionRepository.MAX_DEPTH
}

/**
 * Flattens [all] collections into visible rows, depth first, siblings by sortOrder then id.
 * Children show only when their parent's id is in [expanded]. Rows not reachable from the top
 * level (broken or looped parent links) are left out.
 */
fun buildCollectionTree(all: List<StickerCollection>, expanded: Set<Long>): List<CollectionTreeRow> {
    val byParent = all.groupBy { it.parentId }
        .mapValues { (_, list) -> list.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    val rows = mutableListOf<CollectionTreeRow>()
    val visited = mutableSetOf<Long>()

    fun visit(parentId: Long?, depth: Int) {
        for (c in byParent[parentId].orEmpty()) {
            if (!visited.add(c.id)) continue
            val hasChildren = byParent[c.id].orEmpty().isNotEmpty()
            val isExpanded = hasChildren && c.id in expanded
            rows += CollectionTreeRow(c, depth, hasChildren, isExpanded)
            if (isExpanded) visit(c.id, depth + 1)
        }
    }
    visit(null, 1)
    return rows
}

/** Ids of [id]'s ancestors, top level first. Empty for a top-level or unknown collection. */
fun collectionAncestors(all: List<StickerCollection>, id: Long): List<Long> {
    val byId = all.associateBy { it.id }
    val chain = mutableListOf<Long>()
    var parent = byId[id]?.parentId
    while (parent != null && parent != id && parent !in chain) {
        chain += parent
        parent = byId[parent]?.parentId
    }
    return chain.reversed()
}
