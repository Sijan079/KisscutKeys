package com.example.stickers.shared.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * [FileStore] over app-private folders: `<filesDir>/stickers/` and `<filesDir>/thumbs/`.
 * Pass `context.filesDir` in the app.
 */
class AndroidFileStore(private val filesDir: File) : FileStore {

    override suspend fun write(area: FileArea, relativePath: String, bytes: ByteArray) {
        val target = resolve(area, relativePath)
        withContext(Dispatchers.IO) {
            target.parentFile!!.mkdirs()
            // Write to a temp file, then move into place, so a crash never leaves a half-written file.
            val temp = File.createTempFile("tmp-", ".part", target.parentFile)
            try {
                temp.writeBytes(bytes)
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                temp.delete()
            }
        }
    }

    override suspend fun exists(area: FileArea, relativePath: String): Boolean {
        val file = resolve(area, relativePath)
        return withContext(Dispatchers.IO) { file.isFile }
    }

    override suspend fun delete(area: FileArea, relativePath: String): Boolean {
        val file = resolve(area, relativePath)
        return withContext(Dispatchers.IO) { !file.exists() || file.delete() }
    }

    /** The file behind [relativePath], for image loaders. Same name rules as the other calls. */
    fun file(area: FileArea, relativePath: String): File = resolve(area, relativePath)

    private fun resolve(area: FileArea, name: String): File {
        require(name.matches(SAFE_NAME) && name != "." && name != "..") { "unsafe file name '$name'" }
        val folder = when (area) {
            FileArea.STICKERS -> "stickers"
            FileArea.THUMBS -> "thumbs"
        }
        return File(File(filesDir, folder), name)
    }

    private companion object {
        /** Plain file names only: no separators, so nothing can escape the area folder. */
        val SAFE_NAME = Regex("^[A-Za-z0-9._-]{1,128}$")
    }
}
