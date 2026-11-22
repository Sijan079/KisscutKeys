package app.kisscut.keys.collections

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kisscut.keys.TestLibrary
import app.kisscut.keys.common.DialogTags
import app.kisscut.keys.common.PartTags
import app.kisscut.keys.common.TileTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CollectionsUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val lib = TestLibrary()

    @After
    fun close() = lib.close()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { exists(tag) }
    private fun textShown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private data class Tree(val reactions: Long, val memes: Long, val classics: Long)

    /** Reactions > Memes > Classics, with stickers at each level. */
    private fun seed(): Tree = runBlocking {
        val r = lib.collections.create("Reactions", null)
        val m = lib.collections.create("Memes", r.id)
        val c = lib.collections.create("Classics", m.id)
        lib.face(1, r.id)
        lib.face(2, m.id)
        lib.face(3, c.id)
        lib.face(4, c.id)
        Tree(r.id, m.id, c.id)
    }

    private fun openCollectionsTab() {
        compose.setContent { lib.App() }
        waitFor(PartTags.NAV_COLLECTIONS)
        compose.onNodeWithTag(PartTags.NAV_COLLECTIONS).performClick()
        waitFor(CollectionsTags.LIST)
    }

    @Test
    fun nestedRowsShowTotalsAndExpand() {
        val t = seed()
        openCollectionsTab()
        waitFor(CollectionsTags.row(t.reactions))
        compose.onNodeWithText("4 stickers · 1 subcollection").assertExists()
        assertTrue(!exists(CollectionsTags.row(t.memes)))

        compose.onNodeWithTag(CollectionsTags.toggle(t.reactions)).performClick()
        waitFor(CollectionsTags.row(t.memes))
        compose.onNodeWithTag(CollectionsTags.toggle(t.memes)).performClick()
        waitFor(CollectionsTags.row(t.classics))
        compose.onNodeWithText("2 stickers").assertExists()
    }

    @Test
    fun newCollectionFromTheHeader() {
        openCollectionsTab()
        compose.onNodeWithTag(CollectionsTags.NEW_PILL).performClick()
        waitFor(DialogTags.NAME_FIELD)
        compose.onNodeWithTag(DialogTags.NAME_FIELD).performTextReplacement("Barkada")
        compose.onNodeWithTag(DialogTags.CONFIRM).performClick()
        compose.waitUntil(5_000) { textShown("Barkada") }
        assertEquals(listOf("Barkada"), runBlocking { lib.collections.observeAll().first().map { it.name } })
    }

    @Test
    fun longPressMenuRenames() {
        val t = seed()
        openCollectionsTab()
        waitFor(CollectionsTags.row(t.reactions))
        compose.onNodeWithTag(CollectionsTags.row(t.reactions)).performTouchInput { longClick() }
        waitFor(DialogTags.menu("rename"))
        compose.onNodeWithTag(DialogTags.menu("rename")).performClick()
        waitFor(DialogTags.NAME_FIELD)
        compose.onNodeWithTag(DialogTags.NAME_FIELD).performTextReplacement("Feelings")
        compose.onNodeWithTag(DialogTags.CONFIRM).performClick()
        compose.waitUntil(5_000) { textShown("Feelings") }
    }

    @Test
    fun deleteCollectionOnlyKeepsEverySticker() {
        val t = seed()
        openCollectionsTab()
        waitFor(CollectionsTags.row(t.reactions))
        compose.onNodeWithTag(CollectionsTags.row(t.reactions)).performTouchInput { longClick() }
        waitFor(DialogTags.menu("delete"))
        compose.onNodeWithTag(DialogTags.menu("delete")).performClick()
        waitFor(DialogTags.DELETE_ONLY)
        compose.onNodeWithTag(DialogTags.DELETE_ONLY).performClick()
        compose.waitUntil(5_000) { !exists(CollectionsTags.row(t.reactions)) }
        // Memes moved to the top level; all four stickers still there.
        waitFor(CollectionsTags.row(t.memes))
        assertEquals(4, runBlocking { lib.stickers.allOnce().size })
    }

    @Test
    fun insideACollectionShowsBreadcrumbChildrenAndStickers() {
        val t = seed()
        openCollectionsTab()
        compose.onNodeWithTag(CollectionsTags.toggle(t.reactions)).performClick()
        waitFor(CollectionsTags.row(t.memes))
        compose.onNodeWithTag(CollectionsTags.row(t.memes)).performClick()

        waitFor(DetailTags.TITLE)
        compose.onNodeWithTag(DetailTags.TITLE).assertExists()
        compose.onNodeWithTag(DetailTags.crumb(t.reactions)).assertExists()
        waitFor(DetailTags.child(t.classics))
        val memesSticker = runBlocking { lib.stickers.observeInCollection(t.memes).first().single() }
        waitFor(TileTags.sticker(memesSticker.id))

        // Into the child, then up via the breadcrumb.
        compose.onNodeWithTag(DetailTags.child(t.classics)).performClick()
        waitFor(DetailTags.crumb(t.memes))
        compose.onNodeWithTag(DetailTags.crumb(t.memes)).performClick()
        waitFor(DetailTags.child(t.classics))

        // The Collections tab returns to the list, not this screen.
        compose.onNodeWithTag(PartTags.NAV_COLLECTIONS).performClick()
        waitFor(CollectionsTags.LIST)
    }

    @Test
    fun emptyCollectionSaysNothingHereYet() {
        val id = runBlocking { lib.collections.create("Empty", null).id }
        openCollectionsTab()
        waitFor(CollectionsTags.row(id))
        compose.onNodeWithTag(CollectionsTags.row(id)).performClick()
        waitFor(DetailTags.EMPTY)
        compose.onNodeWithText("Nothing here yet").assertExists()
    }

    @Test
    fun favoritesRowOpensFavorites() {
        val s = runBlocking { lib.face(9).also { lib.stickers.setFavorite(listOf(it.id), true) } }
        openCollectionsTab()
        compose.onNodeWithTag(CollectionsTags.FAVORITES).performClick()
        waitFor(TileTags.sticker(s.id))
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .let { Rect(it.positionInRoot, it.size.toSize()) }

    @Test
    fun longTitleStaysClearOfTheArt() {
        val id = runBlocking {
            lib.collections.create("Mascots and items", null).id.also { c -> repeat(3) { lib.face(it + 1, c) } }
        }
        openCollectionsTab()
        waitFor(CollectionsTags.row(id))
        compose.onNodeWithTag(CollectionsTags.row(id)).performClick()
        waitFor(DetailTags.ART)

        val title = bounds(DetailTags.TITLE)
        val art = bounds(DetailTags.ART)
        assertTrue(title.right <= art.left || title.top >= art.bottom, "title $title runs under the art $art")
    }
}
