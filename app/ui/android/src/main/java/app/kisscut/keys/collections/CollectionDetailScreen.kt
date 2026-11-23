package app.kisscut.keys.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kisscut.keys.R
import app.kisscut.keys.common.CircleIconButton
import app.kisscut.keys.common.CollectionDialogs
import app.kisscut.keys.common.FloatingNav
import app.kisscut.keys.common.HintStyle
import app.kisscut.keys.common.KindChips
import app.kisscut.keys.common.SectionStyle
import app.kisscut.keys.common.SelectionActions
import app.kisscut.keys.common.SelectionHeader
import app.kisscut.keys.common.StickerDialogs
import app.kisscut.keys.common.StickerTile
import app.kisscut.keys.common.TileTags
import app.kisscut.keys.common.TopLevel
import app.kisscut.keys.common.dashedBorder
import app.kisscut.keys.library.libraryColumns
import app.kisscut.keys.shared.core.CollectionSummary
import app.kisscut.keys.shared.db.Sticker
import app.kisscut.keys.ui.theme.Bricolage
import app.kisscut.keys.ui.theme.StickerSizes
import app.kisscut.keys.ui.theme.StickerType
import app.kisscut.keys.ui.theme.StickersTheme

object DetailTags {
    const val GRID = "detail:grid"
    const val TITLE = "detail:title"
    const val BACK = "detail:back"
    const val MORE = "detail:more"
    const val BREADCRUMB = "detail:breadcrumb"
    const val EMPTY = "detail:empty"
    const val NEW_CHILD = "detail:new-child"
    const val ART = "detail:art"
    fun child(id: Long) = "detail-child:$id"
    fun crumb(id: Long?) = "crumb:${id ?: "root"}"
}

private val Side = 20.dp
private val Gap = 10.dp

@Composable
fun CollectionDetailScreen(
    vm: CollectionDetailViewModel,
    onBack: () -> Unit,
    onOpen: (CollectionTarget) -> Unit,
    onCollectionsRoot: () -> Unit,
    onNavigate: (TopLevel) -> Unit,
    onAdd: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val selection = vm.selection
    BackHandler(enabled = state.selection.active) { selection.exit() }
    LaunchedEffect(state.gone) { if (state.gone) onBack() }

    val target = vm.target
    val name = state.name ?: when (target) {
        CollectionTarget.Favorites -> stringResource(R.string.system_favorites)
        CollectionTarget.Unsorted -> stringResource(R.string.system_unsorted)
        is CollectionTarget.Collection -> ""
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val cols = libraryColumns(maxWidth - Side * 2)
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
            modifier = Modifier.fillMaxSize().testTag(DetailTags.GRID),
        ) {
            if (state.selection.active) {
                line("select") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectionHeader(state.selection.count, selection::exit, selection::selectAll)
                        Text(
                            stringResource(R.string.select_hint),
                            style = HintStyle,
                            color = StickersTheme.colors.muted,
                        )
                    }
                }
            } else {
                line("top") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleIconButton(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.cd_back),
                            onBack,
                            Modifier.testTag(DetailTags.BACK),
                        )
                        Box(Modifier.weight(1f))
                        if (target is CollectionTarget.Collection) {
                            CircleIconButton(
                                Icons.Outlined.MoreVert,
                                stringResource(R.string.cd_more_options),
                                { vm.editor.openMenu(target.id) },
                                Modifier.testTag(DetailTags.MORE),
                            )
                        }
                    }
                }
                line("header") { Header(state, name, onCollectionsRoot, onOpen) }
                if (state.children.isNotEmpty() || state.canAddChild) {
                    line("children") {
                        Children(
                            state.children,
                            canAdd = state.canAddChild,
                            onOpen = { onOpen(CollectionTarget.Collection(it)) },
                            onMenu = vm.editor::openMenu,
                            onNew = { (target as? CollectionTarget.Collection)?.let { vm.editor.openNew(it.id) } },
                        )
                    }
                }
                if (state.isEmpty) {
                    line("empty") {
                        Text(
                            stringResource(R.string.view_empty),
                            style = StickerType.Body,
                            color = StickersTheme.colors.muted,
                            modifier = Modifier.padding(vertical = 32.dp).testTag(DetailTags.EMPTY),
                        )
                    }
                } else if (state.directCount > 0) {
                    line("in") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    stringResource(R.string.section_in, name),
                                    style = SectionStyle,
                                    color = StickersTheme.colors.muted,
                                )
                                CountPill(state.directCount.toString(), muted = true)
                            }
                            KindChips(state.kind, vm::setKind)
                        }
                    }
                }
            }
            items(state.stickers, key = { it.id }) { s -> Tile(s, state, name, vm) }
        }

        if (state.selection.active) {
            val chosen = state.stickers.filter { it.id in state.selection.selected }
            SelectionActions(
                count = state.selection.count,
                allFavorite = chosen.isNotEmpty() && chosen.all { it.isFavorite },
                onMove = selection::openMove,
                onFavorite = selection::favorite,
                onDelete = selection::openDelete,
            )
        } else {
            FloatingNav(TopLevel.COLLECTIONS, onNavigate, onAdd)
        }
    }
    StickerDialogs(selection, state.stickerDialog)
    CollectionDialogs(vm.editor, state.collectionDialog)
}

private fun LazyGridScope.line(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun Header(
    state: CollectionDetailUiState,
    name: String,
    onCollectionsRoot: () -> Unit,
    onOpen: (CollectionTarget) -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        if (state.art.isNotEmpty()) {
            StickerArt(state.art, Modifier.align(Alignment.TopEnd).clearAndSetSemantics { testTag = DetailTags.ART })
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(end = if (state.art.isNotEmpty()) StickerArtWidth + 8.dp else 0.dp, top = 6.dp),
        ) {
            Breadcrumb(state, name, onCollectionsRoot, onOpen)
            Text(
                name,
                fontFamily = Bricolage,
                fontWeight = FontWeight(800),
                fontSize = 46.sp,
                lineHeight = 48.sp,
                letterSpacing = (-1.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag(DetailTags.TITLE),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CountPill(countLineStickers(state.totalCount))
                if (state.children.isNotEmpty()) CountPill(countLineChildren(state.children.size))
            }
        }
    }
}

@Composable
private fun countLineStickers(n: Int) = pluralStringResource(R.plurals.count_stickers, n, n)

@Composable
private fun countLineChildren(n: Int) = pluralStringResource(R.plurals.count_subcollections, n, n)

@Composable
private fun CountPill(text: String, muted: Boolean = false) {
    val colors = StickersTheme.colors
    Text(
        text,
        style = StickerType.Label.copy(fontSize = if (muted) 12.sp else 13.sp),
        color = if (muted) colors.muted else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .background(colors.raised, RoundedCornerShape(14.dp))
            .padding(horizontal = if (muted) 9.dp else 12.dp, vertical = if (muted) 2.dp else 6.dp),
    )
}

@Composable
private fun Breadcrumb(
    state: CollectionDetailUiState,
    name: String,
    onCollectionsRoot: () -> Unit,
    onOpen: (CollectionTarget) -> Unit,
) {
    val label = stringResource(R.string.cd_breadcrumb)
    FlowRow(
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { contentDescription = label }.testTag(DetailTags.BREADCRUMB),
    ) {
        Crumb(stringResource(R.string.collections_title), DetailTags.crumb(null), onCollectionsRoot)
        for (c in state.breadcrumb) {
            Chevron()
            Crumb(c.name, DetailTags.crumb(c.id)) { onOpen(CollectionTarget.Collection(c.id)) }
        }
        Chevron()
        Text(name, style = StickerType.Label.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Chevron() = Icon(
    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
    contentDescription = null,
    tint = StickersTheme.colors.muted,
    modifier = Modifier.size(16.dp),
)

@Composable
private fun Crumb(text: String, tag: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .heightIn(min = StickerSizes.MinTouchTarget)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp)
            .testTag(tag),
    ) {
        Text(text, style = StickerType.Label.copy(fontSize = 13.sp), color = StickersTheme.colors.muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Children(
    children: List<CollectionSummary>,
    canAdd: Boolean,
    onOpen: (Long) -> Unit,
    onMenu: (Long) -> Unit,
    onNew: () -> Unit,
) {
    val colors = StickersTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
        if (children.isNotEmpty()) {
            Text(stringResource(R.string.section_subcollections), style = SectionStyle, color = colors.muted)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(children, key = { it.collection.id }) { c ->
                val shape = RoundedCornerShape(26.dp)
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .widthIn(min = 150.dp, max = 190.dp)
                        .heightIn(min = 116.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, colors.line, shape)
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { onOpen(c.collection.id) },
                            onLongClick = { onMenu(c.collection.id) },
                        )
                        .padding(14.dp)
                        .testTag(DetailTags.child(c.collection.id)),
                ) {
                    MiniStickers(c.previews, size = 40.dp, overlap = 12.dp)
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(
                            c.collection.name,
                            fontFamily = Bricolage,
                            fontWeight = FontWeight(700),
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            countLine(c.totalCount, 0),
                            style = StickerType.Body.copy(fontSize = 13.sp),
                            color = colors.muted,
                        )
                    }
                }
            }
            if (canAdd) {
                item(key = "new") {
                    val label = stringResource(R.string.cd_new_subcollection)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                        modifier = Modifier
                            .size(116.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .dashedBorder(colors.dashed, 26.dp)
                            .clickable(role = Role.Button, onClickLabel = label, onClick = onNew)
                            .semantics(mergeDescendants = true) { contentDescription = label }
                            .testTag(DetailTags.NEW_CHILD),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accentText)
                        Text(
                            stringResource(R.string.collections_new),
                            style = StickerType.Label.copy(fontSize = 13.sp),
                            color = colors.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(s: Sticker, state: CollectionDetailUiState, name: String, vm: CollectionDetailViewModel) {
    val selecting = state.selection.active
    StickerTile(
        sticker = s,
        description = stringResource(R.string.cd_sticker_in, name),
        selected = if (selecting) s.id in state.selection.selected else null,
        onTap = { vm.selection.toggle(s.id) },
        onLongPress = { if (selecting) vm.selection.toggle(s.id) else vm.selection.start(s.id) },
        modifier = Modifier.testTag(TileTags.sticker(s.id)),
    )
}
