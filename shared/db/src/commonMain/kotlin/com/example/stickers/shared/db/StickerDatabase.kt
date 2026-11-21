package com.example.stickers.shared.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [StickerCollection::class, Sticker::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(StickerDatabaseConstructor::class)
abstract class StickerDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
    abstract fun stickerDao(): StickerDao
}

// Room generates the actual implementations.
@Suppress("KotlinNoActualForExpect")
expect object StickerDatabaseConstructor : RoomDatabaseConstructor<StickerDatabase> {
    override fun initialize(): StickerDatabase
}

/**
 * Shared configuration for every platform builder: bundled SQLite and IO dispatcher.
 * No destructive migration fallback, ever (docs/DATA_MODEL.md).
 */
fun RoomDatabase.Builder<StickerDatabase>.buildStickerDatabase(): StickerDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/** Runs [block] in one write transaction; DAO calls inside it share the transaction. */
suspend fun <R> StickerDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
