package com.example.stickers.library

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stickers.TestLibrary
import com.example.stickers.common.DialogTags
import com.example.stickers.common.PartTags
import com.example.stickers.common.TileTags
import com.example.stickers.shared.core.StickerKind
import com.example.stickers.shared.db.Sticker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Library on the device with an isolated in-memory library (TESTING.md, UI). */
@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val lib = TestLibrary()

    @After
    fun close() = lib.close()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { exists(tag) }

    private fun tile(s: Sticker) = TileTags.sticker(s.id)

    private fun show() {
        compose.setContent { lib.App() }
    }

    @Test
    fun emptyLibraryOffersAddStickers() {
        show()
        waitFor(LibraryTags.EMPTY)
        compose.onNodeWithTag(LibraryTags.ADD).performClick()
        assertEquals(1, lib.addTaps)
    }

    @Test
    fun showsStickersAndKindChipsFilter() {
        val (a, b, g) = runBlocking { Triple(lib.face(1), lib.face(2), lib.gif()) }
        show()
        waitFor(tile(a))
        compose.onNodeWithTag(LibraryTags.COUNT).assertTextEquals("3")
        assertTrue(exists(tile(b)) && exists(tile(g)))

        compose.onNodeWithTag(PartTags.kind(StickerKind.ANIMATED)).performClick()
        compose.waitUntil(5_000) { !exists(tile(a)) }
        assertTrue(exists(tile(g)))
        assertFalse(exists(tile(b)))

        compose.onNodeWithTag(PartTags.kind(StickerKind.STATIC)).performClick()
        compose.waitUntil(5_000) { !exists(tile(g)) }
        assertTrue(exists(tile(a)) && exists(tile(b)))
    }

    @Test
    fun railFavoritesFiltersAndCollectionsAppear() {
        val (fav, other) = runBlocking {
            val c = lib.collections.create("Reactions", null)
            val fav = lib.face(1, c.id)
            val other = lib.face(2)
            lib.stickers.setFavorite(listOf(fav.id), true)
            fav to other
        }
        show()
        waitFor(tile(other))
        compose.onNodeWithText("Reactions").assertExists()

        compose.onNodeWithTag(LibraryTags.rail(LibraryFilter.Favorites)).performClick()
        compose.waitUntil(5_000) { !exists(tile(other)) }
        compose.onNodeWithTag(LibraryTags.rail(LibraryFilter.Favorites)).assertIsSelected()
        assertTrue(exists(tile(fav)))
    }

    @Test
    fun heroAppearsWithEnoughStickers() {
        runBlocking { repeat(5) { lib.face(it) } }
        show()
        waitFor(LibraryTags.HERO)
    }

    @Test
    fun selectModeCountsTheSelection() {
        val (a, b, c) = runBlocking { Triple(lib.face(1), lib.face(2), lib.face(3)) }
        show()
        waitFor(tile(a))

        compose.onNodeWithTag(tile(a)).performTouchInput { longClick() }
        waitFor(PartTags.SELECT_TITLE)
        compose.onNodeWithTag(PartTags.SELECT_TITLE).assertTextEquals("1 selected")
        compose.onNodeWithTag(tile(a)).assert(isSelected(true))

        compose.onNodeWithTag(tile(b)).performClick()
        compose.onNodeWithTag(PartTags.SELECT_TITLE).assertTextEquals("2 selected")
        compose.onNodeWithTag(tile(a)).performClick()
        compose.onNodeWithTag(PartTags.SELECT_TITLE).assertTextEquals("1 selected")

        compose.onNodeWithTag(PartTags.SELECT_ALL).performClick()
        compose.onNodeWithTag(PartTags.SELECT_TITLE).assertTextEquals("3 selected")

        compose.onNodeWithTag(PartTags.SELECT_CLOSE).performClick()
        compose.waitUntil(5_000) { !exists(PartTags.SELECT_TITLE) }
        assertTrue(exists(tile(c)))
    }

    @Test
    fun longPressIsAvailableToAccessibility() {
        val a = runBlocking { lib.face(1) }
        show()
        waitFor(tile(a))
        compose.onNodeWithTag(tile(a)).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.OnLongClick,
        )
        waitFor(PartTags.SELECT_TITLE)
    }

    @Test
    fun favoriteMoveAndDeleteFromSelectMode() {
        val (a, b) = runBlocking { lib.face(1) to lib.face(2) }
        val target = runBlocking { lib.collections.create("Work", null) }
        show()
        waitFor(tile(a))

        // Favorite
        compose.onNodeWithTag(tile(a)).performTouchInput { longClick() }
        waitFor(PartTags.ACTION_FAVORITE)
        compose.onNodeWithTag(PartTags.ACTION_FAVORITE).performClick()
        compose.waitUntil(5_000) { runBlocking { lib.stickers.allOnce().first { it.id == a.id }.isFavorite } }

        // Move
        compose.onNodeWithTag(tile(a)).performTouchInput { longClick() }
        waitFor(PartTags.ACTION_MOVE)
        compose.onNodeWithTag(PartTags.ACTION_MOVE).performClick()
        waitFor(DialogTags.target(target.id))
        compose.onNodeWithTag(DialogTags.target(target.id)).performClick()
        compose.waitUntil(5_000) {
            runBlocking { lib.stickers.allOnce().first { it.id == a.id }.collectionId == target.id }
        }

        // Delete: row and files gone
        compose.onNodeWithTag(tile(b)).performTouchInput { longClick() }
        waitFor(PartTags.ACTION_DELETE)
        compose.onNodeWithTag(PartTags.ACTION_DELETE).performClick()
        waitFor(DialogTags.CONFIRM)
        compose.onNodeWithTag(DialogTags.CONFIRM).performClick()
        compose.waitUntil(5_000) { !exists(tile(b)) }
        assertEquals(listOf(a.id), runBlocking { lib.stickers.allOnce().map { it.id } })
        assertFalse(lib.fileExists(b))
    }

    @Test
    fun scrollsToTheLastStickerAboveTheNav() {
        val all = runBlocking { (0 until 20).map { lib.face(it) } }
        show()
        waitFor(LibraryTags.GRID)
        compose.onNodeWithTag(LibraryTags.GRID).performScrollToNode(hasTestTag(tile(all.first())))
        compose.onNodeWithTag(tile(all.first())).assertExists()
    }

    private fun isSelected(value: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, value)
}
