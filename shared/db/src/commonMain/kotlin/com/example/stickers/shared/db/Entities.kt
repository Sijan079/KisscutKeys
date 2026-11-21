package com.example.stickers.shared.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A folder of stickers. Nesting via [parentId]; depth cap 3 is enforced in CollectionRepository.
 * The self foreign key cascades as a safety net only: the repository re-parents or deletes
 * children explicitly before removing a row (docs/DATA_MODEL.md, delete rules).
 */
@Entity(
    tableName = "StickerCollection",
    foreignKeys = [
        ForeignKey(
            entity = StickerCollection::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId")],
)
data class StickerCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "Sticker",
    foreignKeys = [
        ForeignKey(
            entity = StickerCollection::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("hash", unique = true),
        Index("collectionId"),
        Index("isFavorite"),
        Index("lastUsedAt"),
    ],
)
data class Sticker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Relative to `files/stickers/`. */
    val filePath: String,
    /** Relative to `files/thumbs/`. */
    val thumbPath: String,
    /** Sniffed from bytes: image/png, image/webp, image/gif. */
    val mimeType: String,
    val isAnimated: Boolean,
    /** True when the image has transparency (cut-line style). */
    val hasAlpha: Boolean,
    /** SHA-256 of the file bytes. Unique. */
    val hash: String,
    /** Null means Unsorted. */
    val collectionId: Long?,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "0") val useCount: Int = 0,
    val lastUsedAt: Long? = null,
    val addedAt: Long,
)

/** A collection plus the number of stickers directly inside it (not counting descendants). */
data class CollectionWithCount(
    @Embedded val collection: StickerCollection,
    val stickerCount: Int,
)
