package com.example.stickers.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsertPlanTest {
    @Test
    fun mimeWildcardsMatchLikeAndroid() {
        assertTrue(mimeMatches("image/png", "image/png"))
        assertTrue(mimeMatches("image/png", "image/*"))
        assertTrue(mimeMatches("image/gif", "*/*"))
        assertTrue(mimeMatches("IMAGE/PNG", "image/png"))
        assertFalse(mimeMatches("image/webp", "image/png"))
        assertFalse(mimeMatches("image/png", "video/*"))
        assertFalse(mimeMatches("image/png", "*/png"))
        assertFalse(mimeMatches("image/png", "garbage"))
    }

    @Test
    fun acceptedTypeIsCommitted() {
        assertEquals(InsertRoute.COMMIT, planInsert("image/webp", false, listOf("image/*"), false))
        assertEquals(InsertRoute.COMMIT, planInsert("image/gif", true, listOf("image/gif", "image/png"), false))
    }

    @Test
    fun stillWebpGoesAsPngWhenOnlyPngIsAccepted() {
        assertEquals(InsertRoute.COMMIT_AS_PNG, planInsert("image/webp", false, listOf("image/png"), false))
        assertEquals(InsertRoute.COMMIT_AS_PNG, planInsert("image/gif", false, listOf("image/png"), false))
    }

    @Test
    fun animatedIsNeverConverted() {
        assertEquals(
            InsertRoute.CLIPBOARD_TYPE_REJECTED,
            planInsert("image/gif", true, listOf("image/png"), false),
        )
    }

    @Test
    fun fieldWithoutImagesFallsBackToClipboard() {
        assertEquals(InsertRoute.CLIPBOARD, planInsert("image/png", false, emptyList(), false))
        assertEquals(InsertRoute.CLIPBOARD, planInsert("image/gif", true, emptyList(), false))
        assertEquals(InsertRoute.CLIPBOARD, planInsert("image/webp", false, listOf("image/jpeg"), false))
    }

    @Test
    fun passwordBlocksEverything() {
        assertEquals(InsertRoute.BLOCKED, planInsert("image/png", false, listOf("image/*"), true))
        assertEquals(InsertRoute.BLOCKED, planInsert("image/png", false, emptyList(), true))
    }
}
