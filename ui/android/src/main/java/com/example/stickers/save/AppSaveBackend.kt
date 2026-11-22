package com.example.stickers.save

import android.content.ContentResolver
import android.net.Uri
import com.example.stickers.shared.core.ByteSource
import com.example.stickers.shared.core.CollectionRepository
import com.example.stickers.shared.core.ImageSniffer
import com.example.stickers.shared.core.ImportLimits
import com.example.stickers.shared.core.ImportResult
import com.example.stickers.shared.core.StickerImporter
import com.example.stickers.shared.core.readCapped
import com.example.stickers.shared.db.StickerCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

/** [SaveBackend] over the real repositories and the content resolver. */
class AppSaveBackend(
    private val resolver: ContentResolver,
    private val collections: CollectionRepository,
    private val importer: StickerImporter,
) : SaveBackend {

    override fun observeCollections(): Flow<List<StickerCollection>> = collections.observeAll()

    override suspend fun createCollection(name: String, parentId: Long?): StickerCollection =
        collections.create(name, parentId)

    override suspend fun describe(uri: String): ItemDetails? {
        val bytes = runCatching { source(uri).readAtMost(ImportLimits.MAX_STICKER_BYTES) }.getOrNull() ?: return null
        if (bytes.size > ImportLimits.MAX_STICKER_BYTES) return null
        val info = ImageSniffer.sniff(bytes) ?: return null
        return ItemDetails(info, bytes.size.toLong())
    }

    override suspend fun import(uri: String, collectionId: Long?, onProgress: (Float) -> Unit): ImportResult =
        importer.import(source(uri), collectionId, onProgress)

    private fun source(uri: String) = ByteSource { max ->
        withContext(Dispatchers.IO) {
            val stream = resolver.openInputStream(Uri.parse(uri)) ?: throw IOException("cannot open $uri")
            stream.use { it.readCapped(max) }
        }
    }
}
