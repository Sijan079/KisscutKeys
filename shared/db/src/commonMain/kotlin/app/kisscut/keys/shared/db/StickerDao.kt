package app.kisscut.keys.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Lists are newest first (addedAt, then id) unless stated otherwise. */
@Dao
interface StickerDao {
    @Query("SELECT * FROM Sticker ORDER BY addedAt DESC, id DESC")
    fun observeAll(): Flow<List<Sticker>>

    /** Stickers directly in [id] (not in its descendants). */
    @Query("SELECT * FROM Sticker WHERE collectionId = :id ORDER BY addedAt DESC, id DESC")
    fun observeInCollection(id: Long): Flow<List<Sticker>>

    /** Stickers in [id] and every collection below it. */
    @Query(
        """
        WITH RECURSIVE tree(id) AS (
          SELECT id FROM StickerCollection WHERE id = :id
          UNION ALL
          SELECT c.id FROM StickerCollection c JOIN tree t ON c.parentId = t.id
        )
        SELECT * FROM Sticker WHERE collectionId IN (SELECT id FROM tree) ORDER BY addedAt DESC, id DESC
        """,
    )
    fun observeInTree(id: Long): Flow<List<Sticker>>

    /** The newest [limit] stickers of every collection (mini previews on collection rows). */
    @Query(
        """
        SELECT * FROM Sticker s WHERE s.collectionId IS NOT NULL AND s.id IN (
          SELECT s2.id FROM Sticker s2 WHERE s2.collectionId = s.collectionId
          ORDER BY s2.addedAt DESC, s2.id DESC LIMIT :limit
        )
        """,
    )
    fun observePreviews(limit: Int): Flow<List<Sticker>>

    @Query("SELECT * FROM Sticker WHERE collectionId IS NULL ORDER BY addedAt DESC, id DESC")
    fun observeUnsorted(): Flow<List<Sticker>>

    @Query("SELECT * FROM Sticker WHERE isFavorite = 1 ORDER BY addedAt DESC, id DESC")
    fun observeFavorites(): Flow<List<Sticker>>

    /** Only stickers used at least once, most recently used first. */
    @Query(
        "SELECT * FROM Sticker WHERE lastUsedAt IS NOT NULL " +
            "ORDER BY lastUsedAt DESC, id DESC LIMIT :limit",
    )
    fun observeRecents(limit: Int): Flow<List<Sticker>>

    @Query("SELECT * FROM Sticker WHERE id = :id")
    suspend fun getById(id: Long): Sticker?

    @Query("SELECT * FROM Sticker WHERE hash = :hash")
    suspend fun findByHash(hash: String): Sticker?

    /** Returns the new row id, or -1 when a sticker with the same hash already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sticker: Sticker): Long

    @Query("UPDATE Sticker SET collectionId = :collectionId WHERE id IN (:ids)")
    suspend fun moveTo(ids: List<Long>, collectionId: Long?)

    @Query("UPDATE Sticker SET isFavorite = :value WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, value: Boolean)

    @Query("UPDATE Sticker SET useCount = useCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Query("SELECT * FROM Sticker WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Sticker>

    @Query("SELECT * FROM Sticker WHERE collectionId IN (:collectionIds)")
    suspend fun inCollections(collectionIds: List<Long>): List<Sticker>

    @Query("UPDATE Sticker SET collectionId = :to WHERE collectionId = :from")
    suspend fun moveAllFromCollection(from: Long, to: Long?)

    @Query("DELETE FROM Sticker WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    /** One-shot list for export. */
    @Query("SELECT * FROM Sticker ORDER BY id")
    suspend fun allOnce(): List<Sticker>
}
