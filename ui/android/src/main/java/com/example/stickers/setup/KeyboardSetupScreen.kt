package com.example.stickers.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stickers.R
import com.example.stickers.ui.theme.Bricolage
import com.example.stickers.ui.theme.SetupFaceColors
import com.example.stickers.ui.theme.StickerShapes
import com.example.stickers.ui.theme.StickerType
import com.example.stickers.ui.theme.StickersTheme

object SetupTags {
    const val SCREEN = "setup"
    const val ACTION = "setup:action"
    const val SKIP = "setup:skip"
    fun step(i: Int) = "setup:step:$i"
}

/**
 * Keyboard setup (Keyboard setup board): three steps whose state follows the system, one main button for the
 * current step, and "Skip for now". [onAction] performs the button's action (open settings, show the
 * keyboard picker, add stickers); [onLeave] runs after Skip or Done.
 */
@Composable
fun KeyboardSetupScreen(vm: KeyboardSetupViewModel, onAction: (SetupAction) -> Unit, onLeave: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val colors = StickersTheme.colors
    val muted = colors.muted

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag(SetupTags.SCREEN),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StickerPile()
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.setup_title),
                    style = StickerType.Title.copy(fontSize = 38.sp, lineHeight = 39.sp, letterSpacing = (-1.2).sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(R.string.setup_body),
                    style = StickerType.Body.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = muted,
                )
            }
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val steps = listOf(
                    R.string.setup_step1_title to R.string.setup_step1_body,
                    R.string.setup_step2_title to R.string.setup_step2_body,
                    R.string.setup_step3_title to R.string.setup_step3_body,
                )
                steps.forEachIndexed { i, (title, body) ->
                    Step(i, state.steps[i], stringResource(title), stringResource(body))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.setup_privacy),
                    style = StickerType.Body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = muted,
                )
            }
        }
        // Pinned below the scrolling content so the button is never cut off.
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp, top = 8.dp)) {
            val label = when (state.action) {
                SetupAction.OPEN_SETTINGS -> R.string.setup_open_settings
                SetupAction.PICK_KEYBOARD -> R.string.setup_pick_keyboard
                SetupAction.ADD_STICKERS -> R.string.setup_add_stickers
                SetupAction.DONE -> R.string.setup_done
            }
            Surface(
                onClick = { if (state.allDone) vm.dismiss(onLeave) else onAction(state.action) },
                shape = StickerShapes.Pill,
                color = colors.lime,
                contentColor = colors.onLime,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(SetupTags.ACTION),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(stringResource(label), style = StickerType.Button.copy(fontSize = 17.sp))
                }
            }
            if (!state.allDone) {
                TextButton(
                    onClick = { vm.dismiss(onLeave) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(SetupTags.SKIP),
                ) {
                    Text(
                        stringResource(R.string.setup_skip),
                        style = StickerType.Body.copy(fontWeight = FontWeight(700)),
                        color = muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Step(index: Int, state: StepState, title: String, body: String) {
    val colors = StickersTheme.colors
    val stateText = when (state) {
        StepState.DONE -> stringResource(R.string.setup_state_done)
        StepState.CURRENT -> stringResource(R.string.setup_state_current)
        StepState.LATER -> stringResource(R.string.setup_state_later)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .testTag(SetupTags.step(index))
            .semantics(mergeDescendants = true) { stateDescription = stateText },
    ) {
        val (fill, ink) = when (state) {
            StepState.DONE -> colors.selection to colors.onSelection
            StepState.CURRENT -> colors.lime to colors.onLime
            StepState.LATER -> colors.raised to colors.muted
        }
        Box(Modifier.size(36.dp).background(fill, CircleShape), contentAlignment = Alignment.Center) {
            if (state == StepState.DONE) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    "${index + 1}",
                    color = ink,
                    fontFamily = Bricolage,
                    fontWeight = FontWeight(800),
                    fontSize = 16.sp,
                )
            }
        }
        Column(Modifier.padding(top = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = StickerType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight(700)),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(body, style = StickerType.Body.copy(fontSize = 14.sp, lineHeight = 19.sp), color = colors.muted)
        }
    }
}

/** Five tilted die-cut faces, as on the board. Decoration only. */
@Composable
private fun StickerPile() {
    val colors = StickersTheme.colors
    // x, y, size, rotation, mouth: positions from the 390dp-wide board, scaled to the screen width.
    val faces = listOf(
        Face(66, 28, 60, 10f, Mouth.FLAT, SetupFaceColors[0]),
        Face(24, 106, 86, -14f, Mouth.SMILE, SetupFaceColors[1]),
        Face(124, 50, 140, -6f, Mouth.OPEN, SetupFaceColors[2]),
        Face(266, 112, 92, 12f, Mouth.O, SetupFaceColors[3]),
        Face(280, 26, 64, -10f, Mouth.SMILE, SetupFaceColors[4]),
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clearAndSetSemantics { },
    ) {
        val scale = (maxWidth.value / 390f).coerceAtMost(1.4f)
        for (f in faces) {
            Canvas(
                Modifier
                    .offset((f.x * scale).dp, (f.y * scale).dp)
                    .size((f.size * scale).dp)
                    .rotate(f.rotation),
            ) {
                drawFace(f, colors.cutLine, colors.stickerInk, colors.stickerShadowAlpha)
            }
        }
    }
}

private enum class Mouth { FLAT, SMILE, OPEN, O }

private data class Face(val x: Int, val y: Int, val size: Int, val rotation: Float, val mouth: Mouth, val color: Color)

/** The board's 48-unit face: shadow, white cut line, colored face, eyes and mouth. */
private fun DrawScope.drawFace(f: Face, cut: Color, ink: Color, shadowAlpha: Float) {
    val u = size.width / 48f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    drawCircle(Color.Black.copy(alpha = shadowAlpha), 19.5f * u, p(24f, 27f))
    drawCircle(cut, 19f * u, p(24f, 24f))
    drawCircle(f.color, 15.5f * u, p(24f, 24f))
    drawCircle(ink, 2.2f * u, p(18.5f, 21f))
    drawCircle(ink, 2.2f * u, p(29.5f, 21f))
    val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round)
    when (f.mouth) {
        Mouth.FLAT -> drawLine(ink, p(18f, 29f), p(30f, 29f), 2f * u, StrokeCap.Round)
        Mouth.SMILE -> drawPath(
            Path().apply { moveTo(17f * u, 28f * u); quadraticTo(24f * u, 35f * u, 31f * u, 28f * u) },
            ink,
            style = stroke,
        )
        Mouth.OPEN -> drawPath(
            Path().apply {
                moveTo(18f * u, 27f * u)
                quadraticTo(24f * u, 39f * u, 30f * u, 27f * u)
                close()
            },
            ink,
        )
        Mouth.O -> drawCircle(ink, 3f * u, p(24f, 29f))
    }
}
