package app.kisscut.keys.shared.core.pack

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `manifest.json` of a `.stickerpack` (docs/IMPORT_EXPORT.md). Ids are local to the pack. */
@Serializable
data class PackManifest(
    val format: String,
    val version: Int,
    val createdAt: Long = 0,
    val appVersion: String = "",
    val collections: List<PackCollection> = emptyList(),
    val stickers: List<PackSticker> = emptyList(),
)

@Serializable
data class PackCollection(
    val id: Long,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class PackSticker(
    /** Path inside the zip, always `stickers/<name>`. */
    val file: String,
    /** SHA-256 of the original bytes; trusted only when [isValidHash]. */
    val hash: String? = null,
    /** Informational only: the real type is sniffed from the bytes. */
    val mimeType: String? = null,
    val collectionId: Long? = null,
    val isFavorite: Boolean = false,
)

/** Optional `settings.json`. Only the keys the app knows are read. */
@Serializable
data class PackSettings(val theme: String? = null)

object PackFormat {
    const val FORMAT = "stickerpack"
    const val VERSION = 1
    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val STICKER_DIR = "stickers/"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/** Why a whole pack is refused. A single bad sticker is skipped instead (see the import summary). */
sealed interface PackProblem {
    /** No manifest, or its `format` is not [PackFormat.FORMAT]. */
    data object NotAPack : PackProblem

    /** Made by a newer app; [version] is higher than [PackFormat.VERSION]. */
    data class TooNew(val version: Int) : PackProblem

    /** The manifest is not valid JSON for this format. */
    data object Malformed : PackProblem

    /** An entry path tries to leave `stickers/` (zip-slip) or is not a plain file name. */
    data class UnsafePath(val path: String) : PackProblem

    /** Less free space than the pack size plus [PackLimits.freeSpaceMargin]. */
    data object NotEnoughSpace : PackProblem

    /** Over a [PackLimits] value: pack size, sticker count, entry count, manifest size or unpacked bytes. */
    data class OverLimit(val what: String) : PackProblem
}

class PackException(val problem: PackProblem) : Exception(problem.toString())

/** Parses and checks a manifest; throws [PackException] for anything the importer must refuse. */
fun parseManifest(bytes: ByteArray, limits: PackLimits = PackLimits()): PackManifest {
    if (bytes.size > limits.maxManifestBytes) throw PackException(PackProblem.OverLimit("manifest"))
    val manifest = try {
        PackFormat.json.decodeFromString(PackManifest.serializer(), bytes.decodeToString())
    } catch (e: SerializationException) {
        throw PackException(PackProblem.Malformed)
    } catch (e: IllegalArgumentException) {
        throw PackException(PackProblem.Malformed)
    }
    if (manifest.format != PackFormat.FORMAT) throw PackException(PackProblem.NotAPack)
    if (manifest.version > PackFormat.VERSION) throw PackException(PackProblem.TooNew(manifest.version))
    if (manifest.version < 1) throw PackException(PackProblem.Malformed)
    if (manifest.stickers.size > limits.maxStickers) throw PackException(PackProblem.OverLimit("stickers"))
    manifest.stickers.firstOrNull { !isSafeStickerPath(it.file) }?.let {
        throw PackException(PackProblem.UnsafePath(it.file))
    }
    return manifest
}

fun encodeManifest(manifest: PackManifest): ByteArray =
    PackFormat.json.encodeToString(PackManifest.serializer(), manifest).encodeToByteArray()

private val SAFE_NAME = Regex("[A-Za-z0-9._-]{1,128}")

/**
 * True for `stickers/<name>` where name is a plain file name: no folders, `..`, drive letters or
 * backslashes. Anything else in a pack is a zip-slip attempt and refuses the whole pack.
 */
fun isSafeStickerPath(path: String): Boolean {
    if (!path.startsWith(PackFormat.STICKER_DIR)) return false
    val name = path.removePrefix(PackFormat.STICKER_DIR)
    return SAFE_NAME.matches(name) && name.any { it != '.' }
}

/** A manifest hash is trusted only as 64 lowercase hex characters, since it becomes a file name. */
fun isValidHash(hash: String?): Boolean =
    hash != null && hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }
