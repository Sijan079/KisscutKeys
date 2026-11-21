package com.example.stickers.shared.core

/** What the import step knows about an image whose files are already stored. */
data class NewSticker(
    val filePath: String,
    val thumbPath: String,
    val mimeType: String,
    val isAnimated: Boolean,
    val hasAlpha: Boolean,
    val hash: String,
)
