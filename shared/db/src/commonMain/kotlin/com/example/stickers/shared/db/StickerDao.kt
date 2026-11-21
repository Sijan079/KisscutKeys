package com.example.stickers.shared.db

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
