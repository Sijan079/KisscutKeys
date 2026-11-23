package app.kisscut.keys.shared.core.pack

import app.kisscut.keys.shared.core.CollectionRepository

/** A pack collection to create, parent first. [parentPackId] is null for the pack's top level. */
data class PlannedCollection(val packId: Long, val name: String, val parentPackId: Long?, val sortOrder: Int)

/**
 * How a pack's collections land in the library (docs/IMPORT_EXPORT.md, Import step 3).
 * [ordered] lists the collections to create, every parent before its children.
 */
data class CollectionPlan(
    val ordered: List<PlannedCollection>,
    /** Pack ids deeper than the cap, mapped to the ancestor their stickers go into. */
    val foldedInto: Map<Long, Long>,
) {
    /** How many collections were flattened into an ancestor (reported to the user). */
    val flattened: Int get() = foldedInto.size

    /** Pack collection a sticker goes into, or null for Unsorted (also for ids the pack never defined). */
    fun targetOf(packCollectionId: Long?): Long? {
        if (packCollectionId == null) return null
        foldedInto[packCollectionId]?.let { return it }
        return packCollectionId.takeIf { it in kept }
    }

    private val kept: Set<Long> = ordered.mapTo(HashSet()) { it.packId }

    companion object {
        /** Name used when a pack's collection name is blank. */
        const val UNTITLED = "Imported"
    }
}

/**
 * Orders [collections] parents first and flattens anything deeper than [maxDepth] into its ancestor at
 * [maxDepth]. Broken input is repaired, never trusted: a repeated id keeps its first entry, and a parent
 * that is missing or part of a cycle makes the collection top level.
 */
fun planCollections(
    collections: List<PackCollection>,
    maxDepth: Int = CollectionRepository.MAX_DEPTH,
): CollectionPlan {
    val byId = LinkedHashMap<Long, PackCollection>()
    collections.forEach { byId.getOrPut(it.id) { it } }

    // Collections in a cycle, or whose parent is missing, become top level; the rest keep their parent.
    // Each collection is walked once (linear even for a hostile pack with one long chain).
    val inCycle = HashSet<Long>()
    val done = HashSet<Long>()
    for (c in byId.values) {
        val path = LinkedHashMap<Long, Int>()
        var at: Long? = c.id
        while (at != null && at in byId && at !in done) {
            val i = path[at]
            if (i != null) {
                inCycle += path.keys.drop(i)
                break
            }
            path[at] = path.size
            at = byId.getValue(at).parentId
        }
        done += path.keys
    }
    val parentOf = byId.values.associate { c ->
        c.id to c.parentId?.takeIf { c.id !in inCycle && it in byId }
    }

    val children = byId.values.groupBy { parentOf[it.id] }
        .mapValues { (_, list) -> list.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    val ordered = mutableListOf<PlannedCollection>()
    val folded = LinkedHashMap<Long, Long>()

    // Recursion stops at maxDepth; deeper subtrees are folded with a loop, so a hostile chain cannot
    // overflow the stack.
    fun fold(top: PackCollection, into: Long) {
        val stack = ArrayDeque(listOf(top))
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            folded[c.id] = into
            stack += children[c.id].orEmpty()
        }
    }

    fun visit(c: PackCollection, depth: Int) {
        val name = c.name.trim().ifEmpty { CollectionPlan.UNTITLED }
        ordered += PlannedCollection(c.id, name, parentOf[c.id], c.sortOrder)
        children[c.id].orEmpty().forEach { if (depth < maxDepth) visit(it, depth + 1) else fold(it, c.id) }
    }
    children[null].orEmpty().forEach { visit(it, 1) }
    return CollectionPlan(ordered, folded)
}
