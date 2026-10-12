package app.otium.ui.transfer

import androidx.compose.runtime.Immutable
import app.otium.domain.error.AppError
import app.otium.domain.transfer.ExportFormat
import app.otium.domain.usecase.ImportPreview
import app.otium.domain.usecase.ImportResult

/** Where an import stands. One import runs at a time. */
sealed interface ImportStep {
    data object Idle : ImportStep

    /** Reading the chosen file and checking it against the library. */
    data object Reading : ImportStep

    /** The file is read; the owner confirms what it would add. */
    data class Previewing(val preview: ImportPreview) : ImportStep

    data class Importing(val done: Int, val total: Int) : ImportStep {
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    }

    data class Done(val result: ImportResult) : ImportStep
}

@Immutable
data class ImportExportUiState(
    val bookCount: Int = 0,
    val step: ImportStep = ImportStep.Idle,
    /** The export being written, if any. */
    val exporting: ExportFormat? = null,
    /** How many books the last export wrote, shown once. */
    val exported: Int? = null,
    val error: AppError? = null,
) {
    val isBusy: Boolean
        get() = step == ImportStep.Reading || step is ImportStep.Importing || exporting != null

    val canExport: Boolean get() = bookCount > 0 && !isBusy
}

/** Everything the import and export screen can ask for. Defaults do nothing. */
@Immutable
data class ImportExportActions(
    val onBack: () -> Unit = {},
    /** Open the system file picker for a library file. */
    val onChooseFile: () -> Unit = {},
    val onImportConfirmed: () -> Unit = {},
    val onImportDismissed: () -> Unit = {},
    val onResultDismissed: () -> Unit = {},
    /** Open the system picker to create the export file. */
    val onExport: (ExportFormat) -> Unit = {},
    val onExportedShown: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)
