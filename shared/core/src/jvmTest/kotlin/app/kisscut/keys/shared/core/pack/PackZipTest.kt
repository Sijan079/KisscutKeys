package app.kisscut.keys.shared.core.pack

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/** `.stickerpack` zips built in the test, including hostile ones (docs/IMPORT_EXPORT.md, Tests). */
class PackZipTest {
    private val manifestJson = """{"format":"stickerpack","version":1,"stickers":[{"file":"stickers/a.png"}]}"""

    /** A raw zip with exactly these entries, in this order. */
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun reader(bytes: ByteArray, limits: PackLimits = PackLimits()) =
        PackZipReader({ ByteArrayInputStream(bytes) }, limits)

    private suspend fun PackZipReader.stickers(): Map<String, ByteArray?> {
        val got = linkedMapOf<String, ByteArray?>()
        readEntries { path, bytes -> got[path] = bytes }
        return got
    }

    private fun problemOf(block: () -> Unit) = assertFailsWith<PackException> { block() }.problem

    @Test
    fun writerOutputReadsBack() = runTest {
        val manifest = PackManifest(PackFormat.FORMAT, 1, stickers = listOf(PackSticker("stickers/a.png")))
        val out = ByteArrayOutputStream()
        PackZipWriter(out).use { w ->
            w.putManifest(manifest)
            w.putSticker("stickers/a.png", byteArrayOf(1, 2, 3))
            w.putSettings(PackSettings(theme = "dark"))
        }
        val r = reader(out.toByteArray())
        assertEquals(manifest, r.readManifest())
        val got = mutableMapOf<String, ByteArray?>()
        val settings = r.readEntries { path, bytes -> got[path] = bytes }
        assertContentEquals(byteArrayOf(1, 2, 3), got["stickers/a.png"])
        assertEquals("dark", PackFormat.json.decodeFromString(PackSettings.serializer(), settings!!.decodeToString()).theme)
    }

    @Test
    fun manifestIsFoundAnywhereInThePack() {
        val bytes = zip("stickers/a.png" to byteArrayOf(9), "manifest.json" to manifestJson.encodeToByteArray())
        assertEquals("stickers/a.png", reader(bytes).readManifest().stickers.single().file)
    }

    @Test
    fun noManifestOrNotAZipIsNotAPack() {
        assertEquals(PackProblem.NotAPack, problemOf { reader(zip("stickers/a.png" to byteArrayOf(1))).readManifest() })
        // Not a zip at all: ZipInputStream sees no entries.
        assertEquals(PackProblem.NotAPack, problemOf { reader("hello".encodeToByteArray()).readManifest() })
    }

    @Test
    fun zipSlipEntriesRefuseTheWholePack() = runTest {
        listOf("../evil.so", "stickers/../../evil.so", "/data/evil", "stickers/sub/a.png", "C:\\evil", "a\\..\\b")
            .forEach { name ->
                val bytes = zip("manifest.json" to manifestJson.encodeToByteArray(), name to byteArrayOf(1))
                val problem = assertFailsWith<PackException> { reader(bytes).stickers() }.problem
                assertEquals(PackProblem.UnsafePath(name), problem, name)
            }
    }

    @Test
    fun anOversizedStickerIsSkippedNotThePack() = runTest {
        val bytes = zip(
            "manifest.json" to manifestJson.encodeToByteArray(),
            "stickers/big.png" to ByteArray(2_000),
            "stickers/ok.png" to ByteArray(10),
        )
        val got = reader(bytes, PackLimits(maxStickerBytes = 1_000)).stickers()
        assertNull(got["stickers/big.png"])
        assertEquals(10, got["stickers/ok.png"]?.size)
    }

    @Test
    fun zipBombStopsAtTheUnpackedLimit() = runTest {
        // 64 MB of zeros compresses to about 64 KB; the limit counts bytes really unpacked.
        val bomb = zip("manifest.json" to manifestJson.encodeToByteArray(), "stickers/a.png" to ByteArray(64 shl 20))
        val limits = PackLimits(maxTotalUnpacked = 8L shl 20)
        assertIs<PackProblem.OverLimit>(assertFailsWith<PackException> { reader(bomb, limits).stickers() }.problem)
    }

    @Test
    fun tooManyEntriesAndTooBigAFileAreRefused() = runTest {
        val many = zip(*Array(20) { "stickers/$it.png" to byteArrayOf(1) })
        assertEquals(
            PackProblem.OverLimit("entries"),
            assertFailsWith<PackException> { reader(many, PackLimits(maxEntries = 10)).stickers() }.problem,
        )
        val big = zip("manifest.json" to manifestJson.encodeToByteArray(), "stickers/a.png" to Random(1).nextBytes(50_000)) // does not compress
        assertEquals(
            PackProblem.OverLimit("pack size"),
            assertFailsWith<PackException> { reader(big, PackLimits(maxPackBytes = 10_000)).stickers() }.problem,
        )
    }

    @Test
    fun aTruncatedPackIsMalformed() = runTest {
        val whole = zip("manifest.json" to manifestJson.encodeToByteArray(), "stickers/a.png" to ByteArray(5_000) { it.toByte() })
        val cut = whole.copyOf(whole.size / 2)
        val problem = assertFailsWith<PackException> { reader(cut).stickers() }.problem
        assertEquals(PackProblem.Malformed, problem)
    }

    @Test
    fun aManifestOverItsLimitIsRefused() {
        val bytes = zip("manifest.json" to manifestJson.encodeToByteArray())
        assertEquals(PackProblem.OverLimit("manifest"), problemOf { reader(bytes, PackLimits(maxManifestBytes = 10)).readManifest() })
    }
}
