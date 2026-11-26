package app.kisscut.keys.save

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import app.kisscut.keys.shared.core.CollectionTreeRow
import app.kisscut.keys.shared.core.ImportResult
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.shared.db.StickerCollection
import app.kisscut.keys.ui.theme.StickersTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import app.kisscut.keys.shared.core.ImageInfo
import app.kisscut.keys.shared.core.ImageType
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Save sheet UI with a fake backend (TESTING.md, UI: Save sheet). */
@RunWith(AndroidJUnit4::class)
class SaveSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val backend = UiFakeBackend()

    private fun show(vararg uris: String, dark: Boolean = true): SaveViewModel {
        val vm = SaveViewModel(backend, uris.toList())
        compose.setContent { StickersTheme(dark = dark) { SaveSheet(vm, onFinished = {}) } }
        compose.waitForIdle()
        return vm
    }

    @Test
    fun gifBadgeIsDarkInLightModeLikeEveryOtherBadge() {
        backend.details = ItemDetails(ImageInfo(ImageType.GIF, 512, 512, isAnimated = true, mayHaveAlpha = true), 1000)
        show("content://test/1", dark = false)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(SaveTags.GIF_BADGE).fetchSemanticsNodes().isNotEmpty()
        }

        val img = compose.onNodeWithTag(SaveTags.GIF_BADGE).captureToImage().toPixelMap()
        val fill = img[2, img.height / 2] // left edge of the pill, clear of the letters
        val ink = Color(0xFF14131C)
        val close = abs(fill.red - ink.red) < 0.05f &&
            abs(fill.green - ink.green) < 0.05f &&
            abs(fill.blue - ink.blue) < 0.05f
        assertTrue(close, "badge fill $fill should be the dark badge color $ink")
    }

    @Test
    fun nestedPickerSelectsTheRightCollection() {
        backend.add(1, "Reactions", null)
        backend.add(2, "Memes", 1)
        backend.add(3, "Work", null)
        show("content://test/1")

        compose.onNodeWithText("Memes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand Reactions").performClick()
        compose.onNodeWithText("Memes").performClick()

        compose.onNodeWithTag(SaveTags.PATH).assertTextEquals("Reactions › Memes")
        compose.onNodeWithTag(SaveTags.SAVE_BUTTON).performClick()
        compose.waitUntil(5_000) { backend.imported.isNotEmpty() }
        assertEquals(listOf<Pair<String, Long?>>("content://test/1" to 2L), backend.imported)
        compose.onNodeWithText("1 sticker saved").assertIsDisplayed()
    }

    @Test
    fun newCollectionIsCreatedInsideTheSelectedOneAndSelected() {
        backend.add(1, "Reactions", null)
        show("content://test/1")

        compose.onNodeWithText("Reactions").performClick()
        compose.onNodeWithText("New collection in Reactions").performClick()
        compose.onNodeWithTag(SaveTags.NEW_COLLECTION_NAME).performTextInput("Cats")
        compose.onNodeWithText("Create").performClick()
        compose.waitForIdle()

        assertEquals(1L, backend.collections.value.single { it.name == "Cats" }.parentId)
        compose.onNodeWithTag(SaveTags.PATH).assertTextEquals("Reactions › Cats")
        compose.onNodeWithText("Save to Cats").assertIsDisplayed()
    }

    @Test
    fun saveButtonStaysVisibleOnAShortScreenWithALongList() {
        val rows = (1L..20L).map { id ->
            CollectionTreeRow(
                StickerCollection(id, "Collection $id", null, id.toInt(), 0),
                depth = 1,
                hasChildren = false,
                isExpanded = false,
            )
        }
        val state = SaveUiState(items = listOf(SaveItem("content://test/1", ItemPreview.Loading)), tree = rows)
        compose.setContent {
            StickersTheme {
                Box(Modifier.height(ShortScreen).testTag("short_screen")) {
                    SaveSheetContent(
                        state,
                        onToggle = {},
                        onSelect = {},
                        onNewCollection = {},
                        onSave = {},
                        onDone = {},
                    )
                }
            }
        }

        val screen = compose.onNodeWithTag("short_screen").fetchSemanticsNode().boundsInRoot
        // positionInRoot + size is unclipped; boundsInRoot would be clipped by the scroll area and hide the bug.
        val node = compose.onNodeWithTag(SaveTags.SAVE_BUTTON).fetchSemanticsNode()
        val top = node.positionInRoot.y
        val bottom = top + node.size.height
        assertTrue(bottom <= screen.bottom + 0.5f, "button bottom $bottom is below screen $screen")
        assertTrue(top >= screen.top, "button top $top is above screen $screen")
    }

    @Test
    fun shrinkingGifShowsItsPercentOnTheButton() {
        val state = SaveUiState(
            items = listOf("content://test/1", "content://test/2").map { SaveItem(it, ItemPreview.Loading) },
            phase = SavePhase.Saving(done = 1, total = 2, percent = 40),
        )
        compose.setContent {
            StickersTheme {
                SaveSheetContent(
                    state,
                    onToggle = {},
                    onSelect = {},
                    onNewCollection = {},
                    onSave = {},
                    onDone = {},
                )
            }
        }
        compose.onNodeWithText("Saving 2 of 2, 40%").assertIsDisplayed()
    }

    @Test
    fun multipleImagesAllSave() {
        show("content://test/1", "content://test/2", "content://test/3")

        compose.onNodeWithText("Save 3 stickers").assertIsDisplayed()
        compose.onNodeWithText("Save to Unsorted").performClick()
        compose.waitUntil(5_000) { backend.imported.size == 3 }

        assertEquals(listOf(null, null, null), backend.imported.map { it.second })
        compose.onNodeWithText("3 stickers saved").assertIsDisplayed()
    }
}

private val ShortScreen = 360.dp

private class UiFakeBackend : SaveBackend {
    val collections = MutableStateFlow<List<StickerCollection>>(emptyList())
    val imported = mutableListOf<Pair<String, Long?>>()
    private var nextId = 100L

    fun add(id: Long, name: String, parentId: Long?) {
        collections.value = collections.value + StickerCollection(id, name, parentId, id.toInt(), 0)
    }

    override fun observeCollections(): Flow<List<StickerCollection>> = collections

    override suspend fun createCollection(name: String, parentId: Long?): StickerCollection =
        StickerCollection(nextId++, name.trim(), parentId, 999, 0).also { collections.value = collections.value + it }

    var details: ItemDetails? = null

    override suspend fun describe(uri: String): ItemDetails? = details

    override suspend fun import(uri: String, collectionId: Long?, onProgress: (Float) -> Unit): ImportResult {
        imported += uri to collectionId
        val n = imported.size.toLong()
        val sticker = Sticker(n, "$n.png", "$n.webp", "image/png", false, true, "h$n", collectionId, addedAt = 0)
        return ImportResult.Added(sticker)
    }
}
