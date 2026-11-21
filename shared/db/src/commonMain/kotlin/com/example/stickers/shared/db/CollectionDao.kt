package com.example.stickers.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Raw collection queries. Rules (depth cap, cycles, delete modes, name trimming) live in
 * CollectionRepository; call that, not this, from UI code.
 */
@Dao
interface CollectionDao {
    /** Direct children of [parentId] (null = top level) with their direct sticker counts. */
    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM Sticker s WHERE s.collectionId = c.id) AS stickerCount
        FROM StickerCollection c
        WHERE c.parentId IS :parentId
        ORDER BY c.sortOrder, c.id
        """,
    )
    fun observeChildren(parentId: Long?): Flow<List<CollectionWithCount>>

    /** Every collection, all levels. */
    @Query("SELECT * FROM StickerCollection ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<StickerCollection>>

    @Query("SELECT * FROM StickerCollection WHERE parentId IS :parentId ORDER BY sortOrder, id")
    suspend fun childrenOnce(parentId: Long?): List<StickerCollection>

    @Query("SELECT * FROM StickerCollection WHERE id = :id")
    fun observeById(id: Long): Flow<StickerCollection?>

    @Query("SELECT * FROM StickerCollection WHERE id = :id")
    suspend fun getById(id: Long): StickerCollection?

    @Insert
    suspend fun insert(collection: StickerCollection): Long

    @Query("UPDATE StickerCollection SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE StickerCollection SET parentId = :newParentId, sortOrder = :newSortOrder WHERE id = :id")
    suspend fun move(id: Long, newParentId: Long?, newSortOrder: Int)

    @Query("UPDATE StickerCollection SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Highest sortOrder among children of [parentId], or null when it has none. */
    @Query("SELECT MAX(sortOrder) FROM StickerCollection WHERE parentId IS :parentId")
    suspend fun maxSortOrder(parentId: Long?): Int?

    /** [id] itself plus every collection below it. */
    @Query(
        """
        WITH RECURSIVE tree(id) AS (
          SELECT id FROM StickerCollection WHERE id = :id
          UNION ALL
          SELECT c.id FROM StickerCollection c JOIN tree t ON c.parentId = t.id
        )
        SELECT id FROM tree
        """,
    )
    suspend fun descendantIds(id: Long): List<Long>

    /** Stickers in [id] and all its descendants. */
    @Query(
        """
        WITH RECURSIVE tree(id) AS (
          SELECT id FROM StickerCollection WHERE id = :id
          UNION ALL
          SELECT c.id FROM StickerCollection c JOIN tree t ON c.parentId = t.id
        )
        SELECT COUNT(*) FROM Sticker WHERE collectionId IN (SELECT id FROM tree)
        """,
    )
    suspend fun totalStickerCount(id: Long): Int

    /** Level of [id]: a top-level collection is 1. Returns 0 if [id] does not exist. */
    @Query(
        """
        WITH RECURSIVE up(id, parentId) AS (
          SELECT id, parentId FROM StickerCollection WHERE id = :id
          UNION ALL
          SELECT c.id, c.parentId FROM StickerCollection c JOIN up u ON c.id = u.parentId
        )
        SELECT COUNT(*) FROM up
        """,
    )
    suspend fun depthOf(id: Long): Int

    /** Number of levels in the subtree rooted at [id], counting [id] itself as 1. */
    @Query(
        """
        WITH RECURSIVE tree(id, level) AS (
          SELECT id, 1 FROM StickerCollection WHERE id = :id
          UNION ALL
          SELECT c.id, t.level + 1 FROM StickerCollection c JOIN tree t ON c.parentId = t.id
        )
        SELECT MAX(level) FROM tree
        """,
    )
    suspend fun subtreeHeight(id: Long): Int

    /** Removes the row only. Callers (CollectionRepository) handle children and stickers first. */
    @Query("DELETE FROM StickerCollection WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM StickerCollection WHERE id IN (:ids)")
    suspend fun deleteRows(ids: List<Long>)
}
