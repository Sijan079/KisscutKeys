package com.example.stickers.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.stickers.R
import com.example.stickers.common.StickerImage
import com.example.stickers.common.tileTint
import com.example.stickers.shared.db.Sticker
import com.example.stickers.ui.theme.StickersTheme

/** "24 stickers · 3 subcollections" (the second part only when there are any). */
@Composable
fun countLine(total: Int, children: Int): String {
    val stickers = pluralStringResource(R.plurals.count_stickers, total, total)
    if (children == 0) return stickers
    return stringResource(
        R.string.count_join,
        stickers,
        pluralStringResource(R.plurals.count_subcollections, children, children),
    )
}

/** Up to three overlapping mini stickers; a folder icon when the collection is empty. */
@Composable
fun MiniStickers(previews: List<Sticker>, size: Dp, overlap: Dp, modifier: Modifier = Modifier) {
    if (previews.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.size(size).clip(CircleShape).background(StickersTheme.colors.raised),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = StickersTheme.colors.muted,
                modifier = Modifier.size(size * 0.5f),
            )
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(-overlap), modifier = modifier) {
        previews.forEach { s ->
            Box(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(size * 0.3f))
                    .background(tileTint(s.id)),
            ) { StickerImage(s, inset = 0.04f, shadowOffset = 1.5.dp) }
        }
    }
}

/** Width of [StickerArt]; the title next to it leaves this much room. */
val StickerArtWidth = 150.dp

/** Three stickers scattered next to a collection title (decoration only). */
@Composable
fun StickerArt(art: List<Sticker>, modifier: Modifier = Modifier) {
    val spots = listOf(
        Triple(44.dp, 0.dp, 84.dp) to 8f,
        Triple(0.dp, 58.dp, 64.dp) to -12f,
        Triple(80.dp, 62.dp, 70.dp) to 14f,
    )
    Box(modifier.size(StickerArtWidth, 132.dp)) {
        // Die-cut stills look best scattered; photos and GIFs only fill in when there are too few.
        art.sortedByDescending { it.hasAlpha && !it.isAnimated }.take(spots.size).forEachIndexed { i, s ->
            val (place, angle) = spots[i]
            val (x, y, side) = place
            Box(
                Modifier
                    .offset(x, y)
                    .size(side)
                    .rotate(angle),
            ) { StickerImage(s, inset = 0f, shadowOffset = 3.dp) }
        }
    }
}
