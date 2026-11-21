package com.example.stickers.shared.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration harness. Each schema version bump adds a test here that:
 * 1. creates the OLD version from its exported JSON (shared/db/schemas/),
 * 2. inserts realistic rows with raw SQL,
 * 3. runs the migration and validates against the new schema,
 * 4. reads the rows back through the DAOs.
 * Never use a destructive fallback (docs/DATA_MODEL.md).
 */
class MigrationTest {
    private val schemaDir: Path = Path(System.getProperty("room.schemaDir") ?: error("room.schemaDir not set"))
    private val tempDir: Path = Files.createTempDirectory("migration")
    private val dbFile: Path = tempDir.resolve("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        schemaDirectoryPath = schemaDir,
        databasePath = dbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = StickerDatabase::class,
    )

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun schemaJsonForVersion1IsCommitted() {
        assertTrue(schemaDir.resolve("com.example.stickers.shared.db.StickerDatabase/1.json").exists())
    }

    @Test
    fun version1DataCreatedFromSchemaJsonOpensThroughRoom() = runTest {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO StickerCollection (id, name, parentId, sortOrder, createdAt) " +
                    "VALUES (1, 'Animals', NULL, 0, 10), (2, 'Cats', 1, 0, 20)",
            )
            // isFavorite and useCount omitted on purpose: their column defaults must apply.
            execSQL(
                "INSERT INTO Sticker (id, filePath, thumbPath, mimeType, isAnimated, hasAlpha, hash, " +
                    "collectionId, lastUsedAt, addedAt) " +
                    "VALUES (7, 'h.png', 'h.webp', 'image/png', 0, 1, 'h', 2, NULL, 30)",
            )
            close()
        }

        // Validates the on-disk v1 schema against the current entities (no migrations yet).
        helper.runMigrationsAndValidate(1, emptyList()).close()

        val db = Room.databaseBuilder<StickerDatabase>(name = dbFile.pathString).buildStickerDatabase()
        try {
            val s = db.stickerDao().findByHash("h")!!
            assertEquals(2L, s.collectionId)
            assertEquals(false, s.isFavorite)
            assertEquals(0, s.useCount)
            assertEquals(true, s.hasAlpha)
            assertEquals(listOf("Cats"), db.collectionDao().observeChildren(1).first().map { it.collection.name })
            assertEquals(1, db.collectionDao().totalStickerCount(1))
        } finally {
            db.close()
        }
    }
}
