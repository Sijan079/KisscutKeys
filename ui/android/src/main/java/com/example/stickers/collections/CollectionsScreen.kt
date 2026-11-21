package com.example.stickers.collections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickers.R
import com.example.stickers.common.CollectionDialogs
import com.example.stickers.common.FloatingNav
import com.example.stickers.common.HintStyle
import com.example.stickers.common.LimePill
import com.example.stickers.common.TopLevel
import com.example.stickers.common.dashedBorder
import com.example.stickers.ui.theme.Bricolage
import com.example.stickers.ui.theme.StickerSizes
import com.example.stickers.ui.theme.StickerType
import com.example.stickers.ui.theme.StickersTheme

object CollectionsTags {
    const val LIST = "collections:list"
    const val NEW_PILL = "collections:new-pill"
    const val NEW_ROW = "collections:new-row"
    const val FAVORITES = "collections:favorites"
    const val UNSORTED = "collections:unsorted"
    fun row(id: Long) = "collection:$id"
    fun toggle(id: Long) = "collection-toggle:$id"
}

@Composable
fun CollectionsScreen(
    vm: CollectionsViewModel,
    onOpen: (CollectionTarget) -> Unit,
    onNavigate: (TopLevel) -> Unit,
    onAdd: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = top + 16.dp,
                bottom = bottom + StickerSizes.NavClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().testTag(CollectionsTags.LIST),
        ) {
            item(key = "title") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.collections_title),
                            style = StickerType.Title,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        LimePill(
                            stringResource(R.string.collections_new),
                            onClick = { vm.editor.openNew(null) },
                            icon = Icons.Outlined.Add,
                            modifier = Modifier.testTag(CollectionsTags.NEW_PILL),
                        )
                    }
                    Text(
                        stringResource(R.string.collections_hint),
                        style = HintStyle,
                        color = StickersTheme.colors.muted,
                    )
                }
            }
            item(key = "favorites") {
                SystemRow(
                    icon = Icons.Filled.Favorite,
                    iconTint = StickersTheme.colors.heart,
                    iconFill = StickersTheme.colors.favoriteTint,
                    name = stringResource(R.string.system_favorites),
                    count = state.favoritesCount,
                    tag = CollectionsTags.FAVORITES,
                    onClick = { onOpen(CollectionTarget.Favorites) },
                )
            }
            items(state.rows, key = { it.id }) { row ->
                TreeRow(
                    row,
                    onOpen = { onOpen(CollectionTarget.Collection(row.id)) },
                    onMenu = { vm.editor.openMenu(row.id) },
                    onToggle = { vm.toggleExpanded(row.id) },
                )
            }
            item(key = "unsorted") {
                SystemRow(
                    icon = Icons.Outlined.Inbox,
                    iconTint = StickersTheme.colors.muted,
                    iconFill = StickersTheme.colors.raised,
                    name = stringResource(R.string.system_unsorted),
                    count = state.unsortedCount,
                    tag = CollectionsTags.UNSORTED,
                    onClick = { onOpen(CollectionTarget.Unsorted) },
                )
            }
            item(key = "new") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .dashedBorder(StickersTheme.colors.dashed, 26.dp)
                        .clickable(role = Role.Button) { vm.editor.openNew(null) }
                        .testTag(CollectionsTags.NEW_ROW),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = StickersTheme.colors.accentText)
                    Text(
                        stringResource(R.string.save_new_collection),
                        style = StickerType.Label.copy(fontSize = 14.sp),
                        color = StickersTheme.colors.muted,
                    )
                }
            }
        }
        FloatingNav(TopLevel.COLLECTIONS, onNavigate, onAdd)
    }
    CollectionDialogs(vm.editor, state.dialog)
}

@Composable
private fun SystemRow(
    icon: ImageVector,
    iconTint: Color,
    iconFill: Color,
    name: String,
    count: Int,
    tag: String,
    onClick: () -> Unit,
) {
    CardRow(
        depth = 1,
        highlighted = false,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).testTag(tag),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(iconFill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        NameAndCount(name, countLine(count, 0), depth = 1, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(row: CollectionRowUi, onOpen: () -> Unit, onMenu: () -> Unit, onToggle: () -> Unit) {
    val s = row.summary
    val depth = s.depth
    val child = depth > 1
    val colors = StickersTheme.colors
    val railColor = colors.line
    val indent = if (child) (12 + (depth - 2) * 22).dp else 0.dp
    val name = s.collection.name
    Box(
        Modifier
            .padding(start = indent)
            .then(
                if (child) {
                    Modifier.drawBehind {
                        drawLine(
                            railColor,
                            Offset(1.dp.toPx(), 0f),
                            Offset(1.dp.toPx(), size.height + 8.dp.toPx()),
                            2.dp.toPx(),
                        )
                    }.padding(start = 10.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        CardRow(
            depth = depth,
            highlighted = !child && row.expanded,
            modifier = Modifier
                .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = onMenu)
                .testTag(CollectionsTags.row(row.id)),
        ) {
            MiniStickers(s.previews, size = if (child) 30.dp else 40.dp, overlap = if (child) 8.dp else 12.dp)
            NameAndCount(name, countLine(s.totalCount, s.childCount), depth, Modifier.weight(1f))
            if (row.hasChildren) {
                val label = stringResource(
                    if (row.expanded) R.string.save_cd_collapse else R.string.save_cd_expand,
                    name,
                )
                IconButton(onClick = onToggle, modifier = Modifier.testTag(CollectionsTags.toggle(row.id))) {
                    Icon(
                        if (row.expanded) Icons.Outlined.KeyboardArrowDown else ArrowRight,
                        contentDescription = label,
                        tint = colors.muted,
                    )
                }
            } else {
                Box(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun CardRow(depth: Int, highlighted: Boolean, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = StickersTheme.colors
    val child = depth > 1
    val shape = RoundedCornerShape(if (child) 22.dp else 26.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (child) 58.dp else 68.dp)
            .clip(shape)
            .background(if (highlighted) colors.raised else MaterialTheme.colorScheme.surface)
            .border(1.dp, colors.line, shape)
            .then(modifier)
            .padding(start = 12.dp, end = 6.dp, top = if (child) 8.dp else 10.dp, bottom = if (child) 8.dp else 10.dp),
        content = content,
    )
}

@Composable
private fun NameAndCount(name: String, count: String, depth: Int, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = modifier) {
        Text(
            name,
            fontFamily = Bricolage,
            fontWeight = FontWeight(700),
            fontSize = if (depth > 1) 16.sp else 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(count, style = StickerType.Body.copy(fontSize = 13.sp), color = StickersTheme.colors.muted)
    }
}

private val ArrowRight get() = Icons.AutoMirrored.Outlined.KeyboardArrowRight
