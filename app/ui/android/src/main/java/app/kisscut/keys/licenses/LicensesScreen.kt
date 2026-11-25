package app.kisscut.keys.licenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kisscut.keys.R
import app.kisscut.keys.common.CircleIconButton
import app.kisscut.keys.ui.theme.StickerType
import app.kisscut.keys.ui.theme.StickersTheme

object LicensesTags {
    const val SCREEN = "licenses"
    const val BACK = "licenses:back"
    const val TEXT = "licenses:text"
    fun row(file: String) = "licenses:row:$file"
}

/** Open-source licenses (docs/UI_SPEC.md): the bundled parts, then one license's full text. */
@Composable
fun LicensesScreen(state: LicensesUiState, onOpen: (License) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val open = state.open
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(LicensesTags.SCREEN)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircleIconButton(
                Icons.AutoMirrored.Outlined.ArrowBack,
                stringResource(R.string.cd_back),
                onBack,
                Modifier.testTag(LicensesTags.BACK),
            )
            Text(
                stringResource(open?.title ?: R.string.licenses_title),
                style = StickerType.Heading,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (open == null) LicenseList(state.licenses, onOpen) else LicenseText(state.text)
    }
}

@Composable
private fun LicenseList(licenses: List<License>, onOpen: (License) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(R.string.licenses_intro),
            style = StickerType.Body,
            color = StickersTheme.colors.muted,
        )
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
            Column {
                licenses.forEachIndexed { i, license ->
                    if (i > 0) HorizontalDivider(color = StickersTheme.colors.line)
                    Surface(
                        onClick = { onOpen(license) },
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().testTag(LicensesTags.row(license.file)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(license.title),
                                    style = StickerType.Body,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(license.about),
                                    style = StickerType.Label,
                                    color = StickersTheme.colors.muted,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = StickersTheme.colors.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseText(text: String?) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text.orEmpty(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 24.dp).testTag(LicensesTags.TEXT),
        )
    }
}
