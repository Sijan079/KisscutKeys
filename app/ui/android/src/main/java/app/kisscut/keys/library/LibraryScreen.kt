package app.kisscut.keys.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kisscut.keys.R
import app.kisscut.keys.common.FloatingNav
import app.kisscut.keys.common.HintStyle
import app.kisscut.keys.common.KindChips
import app.kisscut.keys.common.LimePill
import app.kisscut.keys.common.OutlinePill
import app.kisscut.keys.common.SelectionActions
import app.kisscut.keys.common.SelectionHeader
import app.kisscut.keys.common.StickerDialogs
import app.kisscut.keys.common.StickerImage
import app.kisscut.keys.common.StickerTile
import app.kisscut.keys.common.TileTags
import app.kisscut.keys.common.TopLevel
import app.kisscut.keys.common.tileTint
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.ui.theme.StickerSizes
import app.kisscut.keys.ui.theme.StickerType
import app.kisscut.keys.ui.theme.StickersTheme

object LibraryTags {
    const val GRID = "library:grid"
    const val SELECT = "library:select"
    const val COUNT = "library:count"
    const val EMPTY = "library:empty"
    const val VIEW_EMPTY = "library:view-empty"
    const val ADD = "add_stickers"
    const val HERO = "library:hero"
    internal fun section(s: Section) = "library:section:" + s.name.lowercase()
    fun railLabel(f: LibraryFilter) = rail(f) + ":label"
    fun rail(f: LibraryFilter) = "rail:" + when (f) {
        LibraryFilter.All -> "all"
        LibraryFilter.Favorites -> "favorites"
        LibraryFilter.Recents -> "recents"
        is LibraryFilter.InCollection -> "c${f.id}"
    }
}

private val Gap = 10.dp
private val Side = 20.dp
private val MinCell = 100.dp

/** Columns for a content width: 3 on phones, more on wide screens. */
internal fun libraryColumns(contentWidth: Dp): Int =
    maxOf(3, ((contentWidth + Gap) / (MinCell + Gap)).toInt())

@Composable
fun LibraryScreen(vm: LibraryViewModel, onNavigate: (TopLevel) -> Unit, onAdd: () -> Unit) {
    val state by vm.state.collectAsState()
    val selection = vm.selection
    BackHandler(enabled = state.selection.active) { selection.exit() }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val cols = libraryColumns(maxWidth - Side * 2)
        val cell = (maxWidth - Side * 2 - Gap * (cols - 1)) / cols
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(
                start = Side,
                end = Side,
                top = top + 16.dp,
                bottom = bottom + StickerSizes.NavClearance,
            ),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
            modifier = Modifier.fillMaxSize().testTag(LibraryTags.GRID),
        ) {
            header(state, vm)
            when {
                state.isLibraryEmpty -> fullLine("empty") { EmptyLibrary(onAdd) }
                state.isViewEmpty -> fullLine("view-empty") {
                    Text(
                        stringResource(
                            if (state.filter == LibraryFilter.Recents) R.string.recents_empty else R.string.view_empty,
                        ),
                        style = StickerType.Body,
                        color = StickersTheme.colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp).testTag(LibraryTags.VIEW_EMPTY),
                    )
                }
            }
            val hero = state.hero
            val sections = state.sections
            var rest = state.stickers
            if (sections != null && hero != null) sectionHeader(Section.MOST_USED)
            if (hero != null) {
                val side = if (sections != null) sections.mostUsed.drop(1) else rest.take(2 * (cols - 2))
                if (sections == null) rest = rest.drop(side.size)
                fullLine("hero") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                        StickerTile(
                            sticker = hero.sticker,
                            description = describe(hero.sticker, state.collectionNames),
                            selected = null,
                            onTap = {},
                            onLongPress = { selection.start(hero.sticker.id) },
                            hero = hero.reason,
                            modifier = Modifier.width(cell * 2 + Gap).testTag(LibraryTags.HERO),
                        )
                        side.chunked(2).forEach { pair ->
                            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                                pair.forEach { s -> Tile(s, state, vm, Modifier.width(cell)) }
                            }
                        }
                    }
                }
            }
            if (sections != null) {
                // Section tiles repeat stickers from the grid below, so their keys carry the section name.
                if (sections.new.isNotEmpty()) sectionHeader(Section.NEW)
                items(sections.new, key = { "new:${it.id}" }) { s -> Tile(s, state, vm, Modifier) }
                if (sections.leastUsed.isNotEmpty()) sectionHeader(Section.LEAST_USED)
                items(sections.leastUsed, key = { "least:${it.id}" }) { s -> Tile(s, state, vm, Modifier) }
                sectionHeader(Section.ALL)
            }
            items(rest, key = { it.id }) { s -> Tile(s, state, vm, Modifier) }
        }

        if (state.selection.active) {
            val chosen = state.shown.filter { it.id in state.selection.selected }
            SelectionActions(
                count = state.selection.count,
                allFavorite = chosen.isNotEmpty() && chosen.all { it.isFavorite },
                onMove = selection::openMove,
                onFavorite = selection::favorite,
                onDelete = selection::openDelete,
            )
        } else {
            FloatingNav(TopLevel.LIBRARY, onNavigate, onAdd)
        }
    }
    StickerDialogs(selection, state.dialog)
}

private fun LazyGridScope.fullLine(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/** Library sections on the All view (docs/UI_SPEC.md, Library sections), in screen order. */
internal enum class Section(@param:StringRes val title: Int) {
    MOST_USED(R.string.section_most_used),
    NEW(R.string.section_new),
    LEAST_USED(R.string.section_least_used),
    ALL(R.string.section_all),
}

private fun LazyGridScope.sectionHeader(section: Section) = fullLine("section:${section.name}") {
    Text(
        stringResource(section.title),
        style = StickerType.Heading,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { heading() }
            .testTag(LibraryTags.section(section)),
    )
}

private fun LazyGridScope.header(state: LibraryUiState, vm: LibraryViewModel) {
    if (state.selection.active) {
        fullLine("select-header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionHeader(state.selection.count, vm.selection::exit, vm.selection::selectAll)
                Text(stringResource(R.string.select_hint), style = HintStyle, color = StickersTheme.colors.muted)
            }
        }
        return
    }
    fullLine("title") { TitleRow(state, onSelect = { vm.selection.start() }) }
    if (state.isLibraryEmpty) return
    fullLine("rail") { Rail(state, vm::setFilter) }
    fullLine("kinds") { KindChips(state.kind, vm::setKind) }
}

@Composable
private fun TitleRow(state: LibraryUiState, onSelect: () -> Unit) {
    val colors = StickersTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(R.string.library_title),
                style = StickerType.Title,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (state.loaded) {
                Text(
                    state.totalCount.toString(),
                    style = StickerType.Label.copy(fontSize = 13.sp),
                    color = colors.muted,
                    modifier = Modifier
                        .background(colors.raised, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag(LibraryTags.COUNT),
                )
            }
        }
        if (!state.isLibraryEmpty && state.loaded) {
            OutlinePill(
                stringResource(R.string.library_select),
                onSelect,
                Modifier.testTag(LibraryTags.SELECT),
                filled = true,
            )
        }
    }
}

@Composable
private fun Rail(state: LibraryUiState, onFilter: (LibraryFilter) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(state.rail, key = { LibraryTags.rail(it.filter) }) { item ->
            RailButton(item, selected = item.filter == state.filter, onClick = { onFilter(item.filter) })
        }
    }
}

@Composable
private fun RailButton(item: RailItem, selected: Boolean, onClick: () -> Unit) {
    val colors = StickersTheme.colors
    val label = when (item.filter) {
        LibraryFilter.All -> stringResource(R.string.rail_all)
        LibraryFilter.Favorites -> stringResource(R.string.rail_favorites)
        LibraryFilter.Recents -> stringResource(R.string.rail_recents)
        is LibraryFilter.InCollection -> item.name.orEmpty()
    }
    val tint = when (val f = item.filter) {
        LibraryFilter.All -> colors.tileTints[0]
        LibraryFilter.Favorites -> colors.favoriteTint
        LibraryFilter.Recents -> colors.tileTints[1]
        is LibraryFilter.InCollection -> tileTint(f.id)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(68.dp)
            // No rounded clip here: it shaved the corners of long labels. The ripple is a circle instead.
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 40.dp),
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .testTag(LibraryTags.rail(item.filter)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .border(2.dp, if (selected) colors.accentText else colors.line, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(tint),
        ) {
            when (item.filter) {
                LibraryFilter.All -> RailIcon(Icons.Outlined.GridView, colors.accentText)
                LibraryFilter.Favorites -> RailIcon(Icons.Filled.Favorite, colors.heart)
                LibraryFilter.Recents -> RailIcon(Icons.Outlined.History, colors.violetIcon)
                is LibraryFilter.InCollection -> {
                    val preview = item.preview
                    if (preview != null) {
                        Box(Modifier.fillMaxSize()) { StickerImage(preview, inset = 0.06f, shadowOffset = 2.dp) }
                    } else {
                        RailIcon(Icons.Outlined.Folder, colors.muted)
                    }
                }
            }
        }
        Text(
            label,
            style = StickerType.Label,
            color = if (selected) colors.accentText else colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(LibraryTags.railLabel(item.filter)),
        )
    }
}

@Composable
private fun RailIcon(icon: ImageVector, tint: Color) =
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))

@Composable
private fun Tile(s: Sticker, state: LibraryUiState, vm: LibraryViewModel, modifier: Modifier) {
    val selecting = state.selection.active
    StickerTile(
        sticker = s,
        description = describe(s, state.collectionNames),
        selected = if (selecting) s.id in state.selection.selected else null,
        onTap = { vm.selection.toggle(s.id) },
        onLongPress = { if (selecting) vm.selection.toggle(s.id) else vm.selection.start(s.id) },
        modifier = modifier.testTag(TileTags.sticker(s.id)),
    )
}

/** "Sticker" plus collection name when known (UI_SPEC, accessibility). */
@Composable
internal fun describe(s: Sticker, names: Map<Long, String>): String {
    val name = s.collectionId?.let { names[it] }
    return if (name != null) stringResource(R.string.cd_sticker_in, name) else stringResource(R.string.cd_sticker)
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp).testTag(LibraryTags.EMPTY),
    ) {
        Text(
            stringResource(R.string.library_empty_title),
            style = StickerType.Heading,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.home_empty_body),
            style = StickerType.Body,
            color = StickersTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        LimePill(stringResource(R.string.home_add_stickers), onAdd, Modifier.testTag(LibraryTags.ADD))
    }
}
