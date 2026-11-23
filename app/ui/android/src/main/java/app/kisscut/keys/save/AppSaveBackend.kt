package app.kisscut.keys.save

import android.content.ContentResolver
import android.net.Uri
import app.kisscut.keys.shared.core.ByteSource
import app.kisscut.keys.shared.core.CollectionRepository
import app.kisscut.keys.shared.core.ImageSniffer
import app.kisscut.keys.shared.core.ImportLimits
import app.kisscut.keys.shared.core.ImportResult
import app.kisscut.keys.shared.core.StickerImporter
import app.kisscut.keys.shared.core.readCapped
import app.kisscut.keys.shared.db.StickerCollection
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
