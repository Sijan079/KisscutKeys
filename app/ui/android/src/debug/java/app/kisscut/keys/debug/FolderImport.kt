package app.kisscut.keys.debug

import android.util.Log
import app.kisscut.keys.AppContainer
import app.kisscut.keys.shared.core.ByteSource
import app.kisscut.keys.shared.core.ImportResult
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Debug-only: imports a folder of real images through the real importer. Each sub-folder becomes a collection
 * (nested like the folders, up to the depth cap); files directly in [root] go to Unsorted.
 */
object FolderImport {
    private val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif")

    /** One file to import and the collection path it belongs in (empty for Unsorted). */
    data class Item(val path: List<String>, val file: File)

    /** Files under [root] in a stable order (folders by name, then files by name). Hidden files are skipped. */
    fun plan(root: File): List<Item> {
        fun walk(dir: File, path: List<String>): List<Item> {
            val entries = dir.listFiles().orEmpty().filterNot { it.name.startsWith(".") }.sortedBy { it.name }
            val files = entries.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }.map { Item(path, it) }
            return files + entries.filter { it.isDirectory }.flatMap { walk(it, path + it.name) }
        }
        return walk(root, emptyList())
    }

    /** Returns how many stickers were added. Existing collections with the same name and parent are reused. */
    suspend fun run(c: AppContainer, root: File): Int {
        val ids = mutableMapOf<List<String>, Long>()
        suspend fun collectionFor(path: List<String>): Long? {
            if (path.isEmpty()) return null
            ids[path]?.let { return it }
            val parent = collectionFor(path.dropLast(1))
            val existing = c.collections.observeAll().first()
                .firstOrNull { it.name == path.last() && it.parentId == parent }
            return (existing ?: c.collections.create(path.last(), parent)).id.also { ids[path] = it }
        }
        var added = 0
        for (item in plan(root)) {
            val target = collectionFor(item.path)
            val result = c.importer.import(ByteSource { item.file.readBytes() }, target)
            if (result is ImportResult.Added) added++ else Log.w("SeedData", "${item.file.name}: $result")
        }
        return added
    }
}
