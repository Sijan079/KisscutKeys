package app.kisscut.keys.shared.core.pack

import app.kisscut.keys.shared.core.ByteSource
import app.kisscut.keys.shared.core.ConvertedStill
import app.kisscut.keys.shared.core.CollectionRepository
import app.kisscut.keys.shared.core.FakeFileStore
import app.kisscut.keys.shared.core.FakeStillConverter
import app.kisscut.keys.shared.core.FakeThumbnailer
import app.kisscut.keys.shared.core.ImageType
import app.kisscut.keys.shared.core.ImportResult
import app.kisscut.keys.shared.core.RejectReason
import app.kisscut.keys.shared.core.StickerImporter
import app.kisscut.keys.shared.core.StickerRepository
import app.kisscut.keys.shared.core.TestClock
import app.kisscut.keys.shared.core.TestImages
import app.kisscut.keys.shared.core.inMemoryStickerDatabase
import app.kisscut.keys.shared.core.sha256Hex
import app.kisscut.keys.shared.db.StickerDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Export and import end to end on a real in-memory database (docs/IMPORT_EXPORT.md, Tests). */
class PackRoundTripTest {
    /** One app's library: database, files and the pieces that work on them. */
    private class Library(val limits: PackLimits = PackLimits()) {
        val db: StickerDatabase = inMemoryStickerDatabase()
        val files = FakeFileStore()
        val converter = FakeStillConverter()
        val clock = TestClock()
        val stickers = StickerRepository(db, files, clock)
        val collections = CollectionRepository(db, files, clock)
        val importer = StickerImporter(stickers, files, FakeThumbnailer(), converter)
        val exporter = PackExporter(db, files, clock, "test", limits)
        val packs = PackImporter(db, importer, clock, limits)

        suspend fun save(bytes: ByteArray, collectionId: Long?) =
            assertIs<ImportResult.Added>(importer.import(ByteSource { bytes }, collectionId)).sticker

        suspend fun export(scope: ExportScope = ExportScope.All, settings: PackSettings? = null): ByteArray {
            val out = ByteArrayOutputStream()
            PackZipWriter(out).use { exporter.export(scope, it, settings) }
            return out.toByteArray()
        }

        suspend fun import(pack: ByteArray) = packs.import(PackZipReader({ ByteArrayInputStream(pack) }, limits))

        /** Every sticker as hash to "collection path" ("" for Unsorted), plus favorites. */
        suspend fun snapshot(): Map<String, Pair<String, Boolean>> {
            val byId = collections.observeAll().first().associateBy { it.id }
            fun path(id: Long?): String = if (id == null) "" else byId.getValue(id).let { c ->
                (if (c.parentId == null) "" else path(c.parentId) + "/") + c.name
            }
            return stickers.allOnce().associate { it.hash to (path(it.collectionId) to it.isFavorite) }
        }

        suspend fun collectionPaths() = collections.observeAll().first().map { it.name }.sorted()

        fun close() = db.close()
    }

    private val a = Library()
    private val b = Library()

    @AfterTest
    fun close() {
        a.close()
        b.close()
    }

    private fun png(n: Int) = TestImages.png(width = n)

    /** Cats > Grumpy, Dogs; five stickers, one favorite, one unsorted. */
    private suspend fun Library.fill() {
        val cats = collections.create("Cats", null)
        val grumpy = collections.create("Grumpy", cats.id)
        val dogs = collections.create("Dogs", null)
        save(png(1), cats.id)
        val fav = save(png(2), grumpy.id)
        save(png(3), grumpy.id)
        save(png(4), dogs.id)
        save(png(5), null)
        stickers.setFavorite(listOf(fav.id), true)
    }

    @Test
    fun roundTripIntoAnEmptyLibraryGivesTheSameLibrary() = runTest {
        a.fill()
        val summary = b.import(a.export())

        assertEquals(a.snapshot(), b.snapshot())
        assertEquals(a.collectionPaths(), b.collectionPaths())
        assertEquals(5, summary.added)
        assertEquals(3, summary.collectionsCreated)
        assertEquals(0, summary.rejectedTotal)
    }

    @Test
    fun importingTwiceAndRestoringIntoTheSameLibraryAddNothing() = runTest {
        a.fill()
        val pack = a.export()
        b.import(pack)
        val again = b.import(pack)
        assertEquals(0, again.added)
        assertEquals(5, again.duplicates)
        assertEquals(3, again.collectionsMerged)
        assertEquals(0, again.collectionsCreated)
        assertEquals(5, b.stickers.allOnce().size)

        val restore = a.import(pack)
        assertEquals(0, restore.added)
        assertEquals(3, a.collectionPaths().size, "same-name collections merge")
    }

    @Test
    fun aConvertedStickerKeepsItsOriginalHashThroughABackup() = runTest {
        // Stored bytes differ from the received ones (converted to WebP), but the hash is of the original.
        val webp = TestImages.webpLossless()
        a.converter.next = { ConvertedStill(webp, ImageType.WEBP, 6, 5, scaled = true, rotated = false) }
        val original = TestImages.png(width = 3000, height = 3000)
        val saved = a.save(original, null)
        assertEquals(sha256Hex(original), saved.hash)

        val pack = a.export()
        assertEquals(0, a.import(pack).added, "restore into the same library is a no-op")
        b.import(pack)
        assertEquals(listOf(saved.hash), b.stickers.allOnce().map { it.hash })
        assertTrue(b.stickers.allOnce().single().filePath.endsWith(".webp"))
    }

    @Test
    fun exportingOneCollectionMakesItTheTopLevel() = runTest {
        a.fill()
        val cats = a.collections.observeAll().first().first { it.name == "Cats" }
        b.import(a.export(ExportScope.Collection(cats.id)))

        assertEquals(listOf("Cats", "Grumpy"), b.collectionPaths())
        assertEquals(setOf("Cats", "Cats/Grumpy"), b.snapshot().values.map { it.first }.toSet())
        assertEquals(3, b.stickers.allOnce().size)
    }

    @Test
    fun sameNameMergesIgnoringCaseAndSpaces() = runTest {
        b.collections.create("  cats ", null)
        a.collections.create("Cats", null).also { a.save(png(1), it.id) }
        val summary = b.import(a.export())
        assertEquals(1, summary.collectionsMerged)
        assertEquals(listOf("cats"), b.collectionPaths())
    }

    @Test
    fun settingsTravelWithThePack() = runTest {
        a.fill()
        assertEquals(PackSettings(theme = "dark"), b.import(a.export(settings = PackSettings(theme = "dark"))).settings)
        assertEquals(null, b.import(a.export()).settings)
    }

    // --- hostile or broken packs --------------------------------------------------------------------

    private fun pack(manifest: String, vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(manifest.encodeToByteArray())
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
            }
        }
        return out.toByteArray()
    }

    @Test
    fun badStickersAreSkippedWithReasonsAndTheRestImports() = runTest {
        val manifest = """{"format":"stickerpack","version":1,"stickers":[
            {"file":"stickers/ok.gif"},{"file":"stickers/garbage.png"},{"file":"stickers/png-named.gif"},
            {"file":"stickers/missing.png"}]}"""
        val summary = b.import(
            pack(
                manifest,
                "stickers/ok.gif" to TestImages.gif(),
                "stickers/garbage.png" to "not an image".encodeToByteArray(),
                "stickers/png-named.gif" to png(7), // wrong extension, valid PNG bytes: the bytes win
                "stickers/extra.png" to png(8), // not in the manifest: ignored
            ),
        )
        assertEquals(2, summary.added)
        assertEquals(mapOf(RejectReason.UNSUPPORTED_TYPE to 1), summary.rejected)
        assertEquals(1, summary.missing)
        assertEquals(setOf("image/gif", "image/png"), b.stickers.allOnce().map { it.mimeType }.toSet())
    }

    @Test
    fun deepTreesAreFlattenedAndReported() = runTest {
        val manifest = """{"format":"stickerpack","version":1,"collections":[
            {"id":1,"name":"A"},{"id":2,"name":"B","parentId":1},{"id":3,"name":"C","parentId":2},
            {"id":4,"name":"D","parentId":3}],
            "stickers":[{"file":"stickers/x.png","collectionId":4}]}"""
        val summary = b.import(pack(manifest, "stickers/x.png" to png(1)))
        assertEquals(1, summary.collectionsFlattened)
        assertEquals(mapOf(b.stickers.allOnce().single().hash to ("A/B/C" to false)), b.snapshot())
    }

    @Test
    fun aPackThatBreaksHalfwayLeavesNothingBehind() = runTest {
        a.fill()
        val whole = a.export()
        // Cut inside the last sticker's data (the zip's index at the end is not needed to stream it).
        val lastEntry = (whole.size - 4 downTo 0).first {
            whole[it] == 0x50.toByte() && whole[it + 1] == 0x4B.toByte() && whole[it + 2] == 3.toByte() && whole[it + 3] == 4.toByte()
        }
        val cut = whole.copyOf(lastEntry + 40)

        assertFailsWith<PackException> { b.import(cut) }
        assertEquals(0, b.stickers.allOnce().size)
        assertEquals(emptyList(), b.collectionPaths())
        assertEquals(emptyMap(), b.files.files.toMap(), "files written before the break are removed")
    }

    @Test
    fun cancellingMidwayLeavesNothingBehind() = runTest {
        a.fill()
        val pack = a.export()
        val firstDone = CompletableDeferred<Unit>()
        val slow = object : PackSource {
            val inner = PackZipReader({ ByteArrayInputStream(pack) })
            override fun readManifest() = inner.readManifest()
            override suspend fun readEntries(onSticker: suspend (String, ByteArray?) -> Unit) =
                inner.readEntries { path, bytes ->
                    onSticker(path, bytes)
                    firstDone.complete(Unit)
                    awaitCancellation()
                }
        }
        val job = launch { b.packs.import(slow) }
        firstDone.await()
        assertTrue(b.files.files.isNotEmpty(), "the first sticker's files were written")
        job.cancelAndJoin()

        assertEquals(0, b.stickers.allOnce().size)
        assertEquals(emptyList(), b.collectionPaths())
        assertEquals(emptyMap(), b.files.files.toMap())
    }

    @Test
    fun notEnoughSpaceOrTooBigIsRefusedBeforeReading() = runTest {
        val source = object : PackSource {
            override fun readManifest() = error("must not read")
            override suspend fun readEntries(onSticker: suspend (String, ByteArray?) -> Unit) = error("must not read")
        }
        val limits = PackLimits()
        assertEquals(
            PackProblem.NotEnoughSpace,
            assertFailsWith<PackException> { b.packs.import(source, packSize = 100, freeSpace = limits.freeSpaceMargin) }.problem,
        )
        assertIs<PackProblem.OverLimit>(
            assertFailsWith<PackException> { b.packs.import(source, packSize = limits.maxPackBytes + 1) }.problem,
        )
    }

    // --- export limits -------------------------------------------------------------------------

    @Test
    fun exportRefusesWhatImportWouldRefuse() = runTest {
        val small = Library(PackLimits(maxStickers = 4))
        try {
            small.fill() // 5 stickers
            assertEquals(
                PackProblem.OverLimit("stickers"),
                assertFailsWith<PackException> { small.export() }.problem,
            )
        } finally {
            small.close()
        }
    }

    @Test
    fun aPackExportedAtTheLimitsImportsCleanly() = runTest {
        val limits = PackLimits(maxStickers = 5, maxEntries = 7, maxPackBytes = 20_000)
        val from = Library(limits)
        val to = Library(limits)
        try {
            from.fill()
            val pack = from.export(settings = PackSettings("system"))
            assertTrue(pack.size <= limits.maxPackBytes)
            assertEquals(5, to.import(pack).added)
        } finally {
            from.close()
            to.close()
        }
    }
}
