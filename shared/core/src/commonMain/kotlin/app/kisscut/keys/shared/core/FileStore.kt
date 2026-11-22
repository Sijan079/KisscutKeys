package app.kisscut.keys.shared.core

/** The two app-private folders: originals in `files/stickers/`, thumbnails in `files/thumbs/`. */
enum class FileArea { STICKERS, THUMBS }

/**
 * App-private file storage. Paths are relative to the area folder and must be plain file names
 * (no separators, no `..`); implementations reject anything else with [IllegalArgumentException].
 */
interface FileStore {
    suspend fun write(area: FileArea, relativePath: String, bytes: ByteArray)

    suspend fun exists(area: FileArea, relativePath: String): Boolean

    /** The file's bytes, or null when it does not exist. Used by export. */
    suspend fun read(area: FileArea, relativePath: String): ByteArray?

    /** Deletes the file. Returns false only when a file exists and could not be removed. */
    suspend fun delete(area: FileArea, relativePath: String): Boolean
}
