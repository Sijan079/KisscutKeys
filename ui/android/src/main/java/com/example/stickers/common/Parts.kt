package com.example.stickers.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickers.R
import com.example.stickers.shared.core.StickerKind
import com.example.stickers.ui.theme.Figtree
import com.example.stickers.ui.theme.StickerShapes
import com.example.stickers.ui.theme.StickerSizes
import com.example.stickers.ui.theme.StickerType
import com.example.stickers.ui.theme.StickersTheme

/** Top-level destinations in the floating nav. */
enum class TopLevel { LIBRARY, COLLECTIONS, SETTINGS }

object PartTags {
    const val NAV_LIBRARY = "nav:library"
    const val NAV_COLLECTIONS = "nav:collections"
    const val NAV_ADD = "nav:add"
    const val NAV_SETTINGS = "nav:settings"
    const val SELECT_TITLE = "select:title"
    const val SELECT_ALL = "select:all"
    const val SELECT_CLOSE = "select:close"
    const val ACTION_MOVE = "select:move"
    const val ACTION_FAVORITE = "select:favorite"
    const val ACTION_DELETE = "select:delete"
    fun kind(k: StickerKind) = "kind:${k.name}"
}

/** Body 13/600 muted, the small helper text under titles. */
val HintStyle get() = StickerType.Body.copy(fontSize = 13.sp)

/** Bold 13 section label ("Subcollections"). */
val SectionStyle get() = StickerType.Label.copy(fontSize = 13.sp, fontWeight = FontWeight(800))

/**
 * Pill chip: 36dp to look at, 48dp to touch. Selected uses the inverted fill (UI_SPEC, Parts).
 */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = StickersTheme.colors
    Surface(
        onClick = onClick,
        shape = StickerShapes.Pill,
        color = if (selected) scheme.inverseSurface else scheme.background.copy(alpha = 0f),
        contentColor = if (selected) scheme.inverseOnSurface else colors.muted,
        border = BorderStroke(1.dp, if (selected) scheme.inverseSurface else colors.line),
        modifier = modifier.semantics { this.selected = selected; role = Role.Tab },
    ) {
        Box(
            Modifier.heightIn(min = StickerSizes.ChipHeight).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = StickerType.Label.copy(fontSize = 13.sp))
        }
    }
}

@Composable
fun KindChips(kind: StickerKind, onKind: (StickerKind) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        for ((k, label) in listOf(
            StickerKind.ALL to R.string.kind_all,
            StickerKind.STATIC to R.string.kind_static,
            StickerKind.ANIMATED to R.string.kind_animated,
        )) {
            Chip(
                stringResource(label),
                selected = kind == k,
                onClick = { onKind(k) },
                modifier = Modifier.testTag(PartTags.kind(k)),
            )
        }
    }
}

/** 48dp round icon button with a Line border on Surface (Back, More, Close). */
@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, StickersTheme.colors.line),
        modifier = modifier.size(StickerSizes.MinTouchTarget),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(22.dp),
        ) }
    }
}

/** Outlined pill button (Select, Select all). */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = StickerShapes.Pill,
        color = if (filled) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        border = BorderStroke(1.dp, StickersTheme.colors.line),
        modifier = modifier,
    ) {
        Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = StickerType.Label.copy(fontSize = 14.sp))
        }
    }
}

/** Lime pill (primary): optional leading icon. */
@Composable
fun LimePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = StickersTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = StickerShapes.Pill,
        color = colors.lime.copy(alpha = if (enabled) 1f else 0.4f),
        contentColor = colors.onLime,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.heightIn(min = StickerSizes.PrimaryButtonHeight).padding(
                start = if (icon != null) 14.dp else 18.dp,
                end = 18.dp,
            ),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = StickerType.Button)
        }
    }
}

/** Dashed outline used by every "New" affordance. */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
    )
    val inset = width.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(radius.toPx()),
        style = stroke,
    )
}

/** The only gradient allowed: a short fade behind the floating bar so content does not clash with it. */
@Composable
fun BoxScope.BottomFade() {
    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(150.dp)
            .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.65f to bg, 1f to bg)),
    )
}

/** A floating Raised pill bar 20dp above the bottom; shared by the nav and the selection actions. */
@Composable
fun BoxScope.FloatingBar(
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = StickersTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = arrangement,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(min = StickerSizes.NavBarHeight)
            .background(colors.raised, StickerShapes.Pill)
            .border(1.dp, colors.line, StickerShapes.Pill)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        content = content,
    )
}

/** Floating pill nav: Stickers, Collections, Add (+), Settings. The active one is a lime pill with its label. */
@Composable
fun BoxScope.FloatingNav(current: TopLevel, onSelect: (TopLevel) -> Unit, onAdd: () -> Unit) {
    BottomFade()
    // SpaceBetween plus a shrinkable active pill: at large font sizes the label ellipsizes instead of
    // pushing the last button off the bar.
    FloatingBar(arrangement = Arrangement.SpaceBetween) {
        NavItem(
            TopLevel.LIBRARY,
            current,
            Icons.Outlined.GridView,
            R.string.nav_stickers,
            PartTags.NAV_LIBRARY,
            onSelect,
        )
        NavItem(
            TopLevel.COLLECTIONS,
            current,
            Icons.Outlined.Folder,
            R.string.nav_collections,
            PartTags.NAV_COLLECTIONS,
            onSelect,
        )
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.size(StickerSizes.NavItem).testTag(PartTags.NAV_ADD),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.nav_add),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        NavItem(TopLevel.SETTINGS, current, Icons.Outlined.Tune, R.string.nav_settings, PartTags.NAV_SETTINGS, onSelect)
    }
}

@Composable
private fun RowScope.NavItem(
    item: TopLevel,
    current: TopLevel,
    icon: ImageVector,
    label: Int,
    tag: String,
    onSelect: (TopLevel) -> Unit,
) {
    val colors = StickersTheme.colors
    val active = item == current
    val text = stringResource(label)
    Surface(
        onClick = { onSelect(item) },
        shape = StickerShapes.Pill,
        color = if (active) colors.lime else colors.raised,
        contentColor = if (active) colors.onLime else colors.muted,
        modifier = Modifier
            .then(if (active) Modifier.weight(1f, fill = false) else Modifier)
            .heightIn(min = StickerSizes.NavItem)
            .widthIn(min = StickerSizes.NavItem)
            .testTag(tag)
            .semantics { selected = active },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.padding(horizontal = if (active) 18.dp else 14.dp),
        ) {
            Icon(icon, contentDescription = if (active) null else text, modifier = Modifier.size(22.dp))
            if (active) {
                Text(
                    text,
                    fontFamily = Figtree,
                    fontWeight = FontWeight(800),
                    fontSize = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Select-mode action bar: lime Move, outlined Favorite, outlined danger Delete. */
@Composable
fun BoxScope.SelectionActions(
    count: Int,
    allFavorite: Boolean,
    onMove: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = StickersTheme.colors
    val enabled = count > 0
    BottomFade()
    FloatingBar {
        LimePill(
            stringResource(R.string.action_move),
            onClick = onMove,
            icon = Icons.Outlined.Folder,
            enabled = enabled,
            modifier = Modifier.heightIn(min = StickerSizes.NavItem).testTag(PartTags.ACTION_MOVE),
        )
        Surface(
            onClick = onFavorite,
            enabled = enabled,
            shape = StickerShapes.Pill,
            color = colors.raised,
            contentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.4f),
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier.weight(1f).heightIn(min = StickerSizes.NavItem).testTag(PartTags.ACTION_FAVORITE),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(if (allFavorite) R.string.action_unfavorite else R.string.action_favorite),
                    fontFamily = Figtree,
                    fontWeight = FontWeight(700),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Surface(
            onClick = onDelete,
            enabled = enabled,
            shape = CircleShape,
            color = colors.raised,
            contentColor = colors.danger.copy(alpha = if (enabled) 1f else 0.4f),
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier.size(StickerSizes.NavItem).testTag(PartTags.ACTION_DELETE),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = pluralStringResource(R.plurals.cd_delete_stickers, count, count),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Select-mode header: close, "N selected", Select all. */
@Composable
fun SelectionHeader(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        CircleIconButton(
            Icons.Outlined.Close,
            stringResource(R.string.cd_cancel_selection),
            onClose,
            Modifier.testTag(PartTags.SELECT_CLOSE),
        )
        Text(
            pluralStringResource(R.plurals.select_count, count, count),
            style = StickerType.Title.copy(fontSize = 30.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).testTag(PartTags.SELECT_TITLE),
        )
        OutlinePill(stringResource(R.string.select_all), onSelectAll, Modifier.testTag(PartTags.SELECT_ALL))
    }
}
