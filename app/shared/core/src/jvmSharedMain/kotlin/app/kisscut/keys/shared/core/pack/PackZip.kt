package app.kisscut.keys.shared.core.pack

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads a `.stickerpack` as a stream, never extracting it to disk (docs/IMPORT_EXPORT.md, Import).
 * [open] must return a fresh stream each call: the manifest may sit anywhere in a pack made elsewhere,
 * so it is found in a first pass and the stickers are read in a second.
 *
 * Every limit counts bytes actually read, never the sizes a zip header claims. Breaking one throws
 * [PackException], as does an entry path that tries to leave `stickers/`.
 */
class PackZipReader(
    private val open: () -> InputStream,
    private val limits: PackLimits = PackLimits(),
) : PackSource {

    /** First pass: finds, reads and checks `manifest.json`. */
    override fun readManifest(): PackManifest {
        scan { name, read ->
            if (name == PackFormat.MANIFEST) {
                val bytes = read(limits.maxManifestBytes)
                    ?: throw PackException(PackProblem.OverLimit("manifest"))
                return parseManifest(bytes, limits)
            }
        }
        throw PackException(PackProblem.NotAPack)
    }

    /**
     * Second pass: calls [onSticker] for each `stickers/` file with its bytes, or null when it is over
     * [PackLimits.maxStickerBytes] (that sticker is skipped, not the pack). Returns the bytes of
     * `settings.json` when the pack has one.
     */
    override suspend fun readEntries(onSticker: suspend (path: String, bytes: ByteArray?) -> Unit): ByteArray? {
        var settings: ByteArray? = null
        scan { name, read ->
            currentCoroutineContext().ensureActive()
            when {
                name.startsWith(PackFormat.STICKER_DIR) -> onSticker(name, read(limits.maxStickerBytes))
                name == PackFormat.SETTINGS -> settings = read(limits.maxManifestBytes)
            }
        }
        return settings
    }

    /**
     * Walks every file entry. [visit] gets the name and a reader returning the entry's bytes, or null
     * when it has more than the given cap; unread entries are still drained and counted.
     */
    private inline fun scan(visit: (name: String, read: (cap: Int) -> ByteArray?) -> Unit) {
        val budget = Budget(limits)
        try {
            ZipInputStream(PackSizeGuard(open(), limits.maxPackBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    budget.entry()
                    val name = entry.name
                    checkName(name)
                    if (!entry.isDirectory) visit(name) { cap -> readCapped(zip, cap, budget) }
                    drain(zip, budget)
                }
            }
        } catch (e: ZipException) {
            throw PackException(PackProblem.Malformed)
        } catch (e: EOFException) {
            throw PackException(PackProblem.Malformed) // truncated
        } catch (e: IllegalArgumentException) {
            throw PackException(PackProblem.Malformed) // entry name not valid UTF-8
        }
    }

    private fun checkName(name: String) {
        val unsafe = name.startsWith("/") || name.contains('\\') || name.contains(':') ||
            name.split('/').any { it == ".." }
        val badSticker = name.startsWith(PackFormat.STICKER_DIR) && name != PackFormat.STICKER_DIR &&
            !isSafeStickerPath(name)
        if (unsafe || badSticker) throw PackException(PackProblem.UnsafePath(name))
    }

    private fun readCapped(zip: ZipInputStream, cap: Int, budget: Budget): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = zip.read(buf)
            if (n < 0) return out.toByteArray()
            budget.unpacked(n)
            if (out.size() + n > cap) return null // the rest is drained by scan
            out.write(buf, 0, n)
        }
    }

    private fun drain(zip: ZipInputStream, budget: Budget) {
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = zip.read(buf)
            if (n < 0) return
            budget.unpacked(n)
        }
    }

    private class Budget(private val limits: PackLimits) {
        private var entries = 0
        private var unpacked = 0L

        fun entry() {
            if (++entries > limits.maxEntries) throw PackException(PackProblem.OverLimit("entries"))
        }

        fun unpacked(n: Int) {
            unpacked += n
            if (unpacked > limits.maxTotalUnpacked) throw PackException(PackProblem.OverLimit("unpacked"))
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}

/** Stops reading a pack file that is bigger than [max] bytes, whatever the caller said its size was. */
private class PackSizeGuard(input: InputStream, private val max: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) add(it) }

    override fun skip(n: Long): Long = super.skip(n).also { add(it.toInt()) }

    private fun add(n: Int) {
        count += n
        if (count > max) throw PackException(PackProblem.OverLimit("pack size"))
    }
}

/** Writes a `.stickerpack`: the manifest first, then stickers stored without recompression. */
class PackZipWriter(out: OutputStream) : PackSink, Closeable {
    private val zip = ZipOutputStream(BufferedOutputStream(out))

    override fun putManifest(manifest: PackManifest) = putEntry(PackFormat.MANIFEST, Deflater.DEFAULT_COMPRESSION) {
        it.write(encodeManifest(manifest))
    }

    override fun putSettings(settings: PackSettings) = putEntry(PackFormat.SETTINGS, Deflater.DEFAULT_COMPRESSION) {
        it.write(PackFormat.json.encodeToString(PackSettings.serializer(), settings).encodeToByteArray())
    }

    /** [path] must pass [isSafeStickerPath]. Images are already compressed, so they are not deflated again. */
    override fun putSticker(path: String, bytes: ByteArray) {
        require(isSafeStickerPath(path)) { "unsafe sticker path $path" }
        putEntry(path, Deflater.NO_COMPRESSION) { it.write(bytes) }
    }

    private fun putEntry(name: String, level: Int, write: (OutputStream) -> Unit) {
        zip.setLevel(level)
        zip.putNextEntry(ZipEntry(name))
        write(zip)
        zip.closeEntry()
    }

    override fun close() = zip.close()
}
