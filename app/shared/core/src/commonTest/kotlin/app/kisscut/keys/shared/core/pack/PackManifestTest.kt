package app.kisscut.keys.shared.core.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PackManifestTest {
    private fun problem(json: String, limits: PackLimits = PackLimits()) =
        assertFailsWith<PackException> { parseManifest(json.encodeToByteArray(), limits) }.problem

    private val hash = "ab".repeat(32)

    @Test
    fun roundTripsAndIgnoresUnknownKeys() {
        val m = PackManifest(
            format = PackFormat.FORMAT, version = 1, createdAt = 5, appVersion = "1.0",
            collections = listOf(PackCollection(1, "Cats", null, 0)),
            stickers = listOf(PackSticker("stickers/$hash.webp", hash, "image/webp", 1, true)),
        )
        assertEquals(m, parseManifest(encodeManifest(m)))

        val extra = """{"format":"stickerpack","version":1,"future":{"x":1},
            "stickers":[{"file":"stickers/a.png","sparkle":true}]}"""
        assertEquals(listOf(PackSticker("stickers/a.png")), parseManifest(extra.encodeToByteArray()).stickers)
    }

    @Test
    fun refusesOtherFormatsNewerVersionsAndGarbage() {
        assertEquals(PackProblem.NotAPack, problem("""{"format":"zip","version":1}"""))
        assertEquals(PackProblem.TooNew(2), problem("""{"format":"stickerpack","version":2}"""))
        assertEquals(PackProblem.Malformed, problem("""{"format":"stickerpack","version":0}"""))
        assertEquals(PackProblem.Malformed, problem("not json"))
        assertEquals(PackProblem.Malformed, problem("""{"version":1}"""))
        assertEquals(PackProblem.Malformed, problem("""{"format":"stickerpack","version":"one"}"""))
    }

    @Test
    fun refusesUnsafeStickerPathsAndLimits() {
        val bad = """{"format":"stickerpack","version":1,"stickers":[{"file":"stickers/../../evil.so"}]}"""
        assertEquals(PackProblem.UnsafePath("stickers/../../evil.so"), problem(bad))

        val three = """{"format":"stickerpack","version":1,"stickers":[
            {"file":"stickers/a.png"},{"file":"stickers/b.png"},{"file":"stickers/c.png"}]}"""
        assertIs<PackProblem.OverLimit>(problem(three, PackLimits(maxStickers = 2)))
        assertIs<PackProblem.OverLimit>(problem(three, PackLimits(maxManifestBytes = 20)))
    }

    @Test
    fun safeStickerPaths() {
        assertTrue(isSafeStickerPath("stickers/$hash.webp"))
        assertTrue(isSafeStickerPath("stickers/a-b_c.1.gif"))
        listOf(
            "stickers/../x.png", "../x.png", "/etc/passwd", "stickers/a/b.png", "stickers/..", "stickers/.",
            "stickers/", "stickers\\..\\x.png", "C:\\x.png", "stickers/c:x.png", "other/a.png",
            "stickers/" + "a".repeat(129), "stickers/a b.png",
        ).forEach { assertFalse(isSafeStickerPath(it), it) }
    }

    @Test
    fun onlyLowercaseHexHashesAreTrusted() {
        assertTrue(isValidHash(hash))
        assertFalse(isValidHash(null))
        assertFalse(isValidHash(hash.uppercase()))
        assertFalse(isValidHash(hash.drop(1)))
        assertFalse(isValidHash("../" + hash.drop(3)))
    }
}
