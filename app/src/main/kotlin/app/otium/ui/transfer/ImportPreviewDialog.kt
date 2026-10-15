package app.otium.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.usecase.ImportPreview
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumDialog

/** What the file would add, before anything is written. */
@Composable
internal fun PreviewDialog(preview: ImportPreview, actions: ImportExportActions) {
    val canImport = preview.newBooks > 0
    OtiumDialog(
        title = if (canImport) {
            pluralStringResource(R.plurals.import_preview_title, preview.newBooks, preview.newBooks)
        } else {
            stringResource(R.string.import_preview_nothing_title)
        },
        onDismissRequest = actions.onImportDismissed,
        actions = {
            OtiumButton(
                text = stringResource(
                    if (canImport) R.string.import_cancel else R.string.import_close,
                ),
                onClick = actions.onImportDismissed,
                style = OtiumButtonStyle.Secondary,
            )
            if (canImport) {
                OtiumButton(
                    text = stringResource(R.string.import_confirm_action),
                    onClick = actions.onImportConfirmed,
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Note(
                stringResource(
                    R.string.import_preview_from,
                    stringResource(preview.format.nameRes()),
                ),
            )
            if (preview.alreadyInLibrary > 0) {
                val count = preview.alreadyInLibrary
                Note(pluralStringResource(R.plurals.import_preview_already, count, count))
            }
            if (canImport && preview.withoutIsbn > 0) {
                val count = preview.withoutIsbn
                Note(pluralStringResource(R.plurals.import_preview_without_isbn, count, count))
            }
            if (preview.unreadable > 0) {
                val count = preview.unreadable
                Note(pluralStringResource(R.plurals.import_preview_unreadable, count, count))
            }
            if (canImport && preview.format != LibraryFormat.OTIUM) {
                Note(stringResource(R.string.import_preview_dropped))
            }
        }
    }
}

private fun LibraryFormat.nameRes(): Int = when (this) {
    LibraryFormat.GOODREADS -> R.string.format_goodreads
    LibraryFormat.STORYGRAPH -> R.string.format_storygraph
    LibraryFormat.OTIUM -> R.string.format_otium
}
