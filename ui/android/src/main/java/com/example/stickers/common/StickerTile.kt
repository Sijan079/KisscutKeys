package com.example.stickers.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.stickers.R
import com.example.stickers.shared.core.HeroReason
import com.example.stickers.shared.db.Sticker
import com.example.stickers.ui.theme.Figtree
import com.example.stickers.ui.theme.StickersTheme

/** Turns a sticker into an image model Coil can load (its thumbnail file). Set by the activity. */
val LocalThumbnails = staticCompositionLocalOf<(Sticker) -> Any?> { { null } }

/** Stable tile fill for a sticker, so it keeps its color as the grid changes. */
@Composable
fun tileTint(id: Long): Color {
    val tints = StickersTheme.colors.tileTints
    return tints[(id % tints.size).toInt()]
}

/** Test tags and semantics helpers for sticker tiles. */
object TileTags {
    fun sticker(id: Long) = "sticker:$id"
    fun gifBadge(id: Long) = "badge:gif:$id"
    const val HERO_BADGE = "badge:hero"
}

/**
 * One sticker in the die-cut look (docs/UI_SPEC.md, Sticker rendering):
 * transparent stills sit on a tinted tile with the baked cut line plus a drawn shadow; opaque images fill a
 * rounded tile; animated ones are a plain tile with a GIF badge. [selected] null means not in select mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerTile(
    sticker: Sticker,
    description: String,
    selected: Boolean?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    hero: HeroReason? = null,
) {
    val colors = StickersTheme.colors
    val shape = RoundedCornerShape(if (hero != null) 28.dp else 20.dp)
    val selectLabel = stringResource(R.string.action_select)
    val stateText = when (selected) {
        true -> stringResource(R.string.state_selected)
        false -> stringResource(R.string.state_not_selected)
        null -> null
    }
    val favoriteText = stringResource(R.string.cd_favorite)
    val animatedText = stringResource(R.string.cd_animated)
    val fullDescription = buildList {
        add(description)
        if (sticker.isAnimated) add(animatedText)
        if (sticker.isFavorite) add(favoriteText)
    }.joinToString(", ")

    val interaction = if (selected != null) {
        Modifier
            .semantics { this.selected = selected }
            .combinedClickable(role = Role.Checkbox, onClick = onTap, onLongClick = onTap)
    } else {
        // Outside select mode a tile only reacts to a long press (there is no sticker viewer in v1).
        Modifier
            .semantics { onLongClick(label = selectLabel) { onLongPress(); true } }
            .pointerInput(sticker.id) { detectTapGestures(onLongPress = { onLongPress() }) }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(tileTint(sticker.id))
            .then(if (selected == true) Modifier.border(3.dp, colors.selection, shape) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = fullDescription
                if (stateText != null) stateDescription = stateText
            }
            .then(interaction),
    ) {
        StickerImage(
            sticker,
            inset = if (hero != null) 0.14f else 0.12f,
            shadowOffset = if (hero != null) 6.dp else 3.dp,
        )
        val gif = stringResource(R.string.save_badge_gif)
        if (hero != null) {
            val label = when (hero) {
                HeroReason.MOST_USED -> stringResource(R.string.hero_most_used)
                HeroReason.NEWEST -> stringResource(R.string.hero_newest)
            }
            // Side by side: both sit bottom-left, so stacking them would hide the GIF badge.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Badge(label, textColor = colors.lime, big = true, tag = TileTags.HERO_BADGE)
                if (sticker.isAnimated) Badge(gif, tag = TileTags.gifBadge(sticker.id))
            }
        } else if (sticker.isAnimated) {
            Badge(gif, Modifier.align(Alignment.BottomStart).padding(8.dp), tag = TileTags.gifBadge(sticker.id))
        }
        if (sticker.isFavorite && selected == null) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(16.dp),
            )
        }
        if (selected != null) SelectionMark(selected)
    }
}

/** The sticker picture alone, filling its parent (also used in collection previews). */
@Composable
fun BoxScope.StickerImage(sticker: Sticker, inset: Float = 0.12f, shadowOffset: Dp = 3.dp) {
    val model = LocalThumbnails.current(sticker) ?: return
    val colors = StickersTheme.colors
    if (sticker.hasAlpha && !sticker.isAnimated) {
        // Cut line is baked into the thumbnail; the shadow is the same image tinted black and offset down.
        Box(Modifier.fillMaxSize(1f - 2 * inset).align(Alignment.Center)) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = colors.stickerShadowAlpha), BlendMode.SrcIn),
                modifier = Modifier.fillMaxSize().offset(y = shadowOffset),
            )
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else if (sticker.hasAlpha) {
        // Animated with transparency: plain tile, image fitted, no cut line or shadow.
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(1f - 2 * inset).align(Alignment.Center),
        )
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    big: Boolean = false,
    tag: String? = null,
) {
    val colors = StickersTheme.colors
    Text(
        text = text,
        color = textColor ?: colors.badgeText,
        fontFamily = Figtree,
        fontWeight = FontWeight(800),
        fontSize = if (big) 11.sp else 10.sp,
        letterSpacing = if (big) 0.6.sp else 0.5.sp,
        modifier = modifier
            .background(colors.badgeFill, RoundedCornerShape(if (big) 12.dp else 8.dp))
            .padding(horizontal = if (big) 11.dp else 7.dp, vertical = if (big) 5.dp else 2.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

@Composable
private fun BoxScope.SelectionMark(selected: Boolean) {
    val colors = StickersTheme.colors
    val base = Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp)
    if (selected) {
        Box(base.background(colors.selection, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onSelection,
                modifier = Modifier.size(18.dp),
            )
        }
    } else {
        Box(
            base
                .background(MaterialTheme.colorScheme.background, CircleShape)
                .border(2.dp, colors.muted, CircleShape),
        )
    }
}
