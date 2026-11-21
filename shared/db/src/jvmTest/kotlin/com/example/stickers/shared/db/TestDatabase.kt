package com.example.stickers.shared.db

import androidx.room.Room

fun inMemoryStickerDatabase(): StickerDatabase =
    Room.inMemoryDatabaseBuilder<StickerDatabase>().buildStickerDatabase()

/** Minimal valid sticker for tests; override what matters. */
fun testSticker(
    hash: String,
    collectionId: Long? = null,
    addedAt: Long = 1_000L,
    isFavorite: Boolean = false,
    lastUsedAt: Long? = null,
) = Sticker(
    filePath = "$hash.png",
    thumbPath = "$hash.webp",
    mimeType = "image/png",
    isAnimated = false,
    hasAlpha = true,
    hash = hash,
    collectionId = collectionId,
    isFavorite = isFavorite,
    lastUsedAt = lastUsedAt,
    addedAt = addedAt,
)
