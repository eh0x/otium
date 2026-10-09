package app.otium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.SheetShape
import app.otium.ui.theme.otiumColors

private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 3.dp
private val DIALOG_MAX_WIDTH = 400.dp

/**
 * A bottom sheet in the Otium style: sheet fill, hairline edge, 14 dp top corners, a slim drag
 * handle and the themed scrim. Content gets the 22 dp page gutter and room at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtiumSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.otiumColors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.border(1.dp, colors.divider, SheetShape),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = colors.sheet,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 30.dp),
            content = content,
        )
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 14.dp)
            .size(HANDLE_WIDTH, HANDLE_HEIGHT)
            .background(MaterialTheme.otiumColors.divider, MaterialTheme.shapes.extraSmall),
    )
}

/**
 * A dialog in the Otium style: sheet fill, hairline border, 8 dp corners, a serif title, a quiet
 * body and actions aligned to the end (put the secondary action first, then the primary).
 */
@Composable
fun OtiumDialog(
    title: String,
    onDismissRequest: () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismissRequest) {
        OtiumDialogCard(title = title, actions = actions, modifier = modifier, body = body, content)
    }
}

/** The card a dialog draws, separate from the window so it can be previewed and tested. */
@Composable
internal fun OtiumDialogCard(
    title: String,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = MaterialTheme.otiumColors
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .widthIn(max = DIALOG_MAX_WIDTH)
            .background(colors.sheet, shape)
            .border(1.dp, colors.divider, shape)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 30.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (body != null) {
            Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
        content()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            actions()
        }
    }
}

@PreviewLightDark
@Composable
private fun OtiumDialogPreview() {
    OtiumTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(22.dp)) {
            OtiumDialogCard(
                title = "Delete this book?",
                body = "“Meditations” will be removed from your library. This cannot be undone.",
                actions = {
                    OtiumButton("Cancel", {}, style = OtiumButtonStyle.Secondary)
                    OtiumButton("Delete", {})
                },
            )
        }
    }
}
