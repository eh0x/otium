package app.otium.ui.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.domain.transfer.ExportFormat
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.usecase.ImportPreview
import app.otium.domain.usecase.ImportResult
import app.otium.ui.backup.BusyIndicator
import app.otium.ui.backup.SectionTitle
import app.otium.ui.common.messageRes
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumProgressBar
import app.otium.ui.components.OtiumScreenHeader
import app.otium.ui.components.OtiumSnackbarHost
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors
import java.time.LocalDate

/** What the picker offers: exports are CSV or JSON, and email often loses the file type. */
private val IMPORT_TYPES = arrayOf("text/*", "application/json", "application/octet-stream")

/** Connects the import and export screen to its ViewModel and to the system file pickers. */
@Composable
fun ImportExportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportExportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.onFilePicked(it?.toString())
    }
    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { viewModel.onExport(it?.toString(), ExportFormat.CSV) }
    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { viewModel.onExport(it?.toString(), ExportFormat.JSON) }

    val actions = remember(viewModel, onBack) {
        ImportExportActions(
            onBack = onBack,
            onChooseFile = { pickFile.launch(IMPORT_TYPES) },
            onImportConfirmed = viewModel::onImportConfirmed,
            onImportDismissed = viewModel::onImportDismissed,
            onResultDismissed = viewModel::onResultDismissed,
            onExport = { format ->
                val name = "otium-library-${LocalDate.now()}"
                when (format) {
                    ExportFormat.CSV -> createCsv.launch("$name.csv")
                    ExportFormat.JSON -> createJson.launch("$name.json")
                }
            },
            onExportedShown = viewModel::onExportedShown,
            onErrorShown = viewModel::onErrorShown,
        )
    }
    ImportExportContent(state = state, actions = actions, modifier = modifier)
}

/** The import and export screen as a pure function of [state]. */
@Composable
fun ImportExportContent(
    state: ImportExportUiState,
    actions: ImportExportActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    Notices(snackbar, state, actions)

    PaperBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            OtiumScreenHeader(
                title = stringResource(R.string.transfer_title),
                onBack = actions.onBack,
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp),
            ) {
                Section(first = true) { ImportSection(state, actions) }
                Section { ImportHelp() }
                Section { ExportSection(state, actions) }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            OtiumSnackbarHost(hostState = snackbar)
        }
        (state.step as? ImportStep.Previewing)?.let { PreviewDialog(it.preview, actions) }
    }
}

/** Shows the export count or an error once in [snackbar], then reports it shown. */
@Composable
private fun Notices(
    snackbar: SnackbarHostState,
    state: ImportExportUiState,
    actions: ImportExportActions,
) {
    val exportedText = state.exported?.let {
        pluralStringResource(R.plurals.export_done, it, it)
    }
    val errorText = state.error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(exportedText) {
        if (exportedText != null) {
            snackbar.showSnackbar(exportedText)
            actions.onExportedShown()
        }
    }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbar.showSnackbar(errorText)
            actions.onErrorShown()
        }
    }
}

/** A section under a hairline, spaced like the settings page. */
@Composable
private fun Section(first: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.hairlineTop(MaterialTheme.otiumColors.divider))
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        color = MaterialTheme.otiumColors.muted,
    )
}

@Composable
private fun ImportSection(state: ImportExportUiState, actions: ImportExportActions) {
    SectionTitle(stringResource(R.string.import_heading))
    Note(stringResource(R.string.import_note))
    OtiumButton(
        text = stringResource(R.string.import_action),
        onClick = actions.onChooseFile,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth(),
    )
    when (val step = state.step) {
        ImportStep.Reading -> BusyIndicator(stringResource(R.string.import_reading))

        is ImportStep.Importing -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OtiumProgressBar(progress = step.fraction)
            Note(stringResource(R.string.import_progress, step.done, step.total))
        }

        is ImportStep.Done -> ImportDone(step.result, actions.onResultDismissed)

        ImportStep.Idle, is ImportStep.Previewing -> Unit
    }
}

@Composable
private fun ImportDone(result: ImportResult, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = pluralStringResource(R.plurals.import_done, result.added, result.added),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (result.skipped > 0) {
            Note(
                pluralStringResource(R.plurals.import_done_skipped, result.skipped, result.skipped),
            )
        }
        if (result.waitingForLookup > 0) {
            val count = result.waitingForLookup
            Note(pluralStringResource(R.plurals.import_done_waiting, count, count))
        }
        if (result.failed > 0) {
            Note(pluralStringResource(R.plurals.import_done_failed, result.failed, result.failed))
        }
        if (result.notesAdded > 0) {
            val count = result.notesAdded
            Note(pluralStringResource(R.plurals.import_done_notes, count, count))
        }
        if (result.notesSkipped > 0) {
            val count = result.notesSkipped
            Note(pluralStringResource(R.plurals.import_done_notes_skipped, count, count))
        }
        if (result.loansAdded > 0) {
            val count = result.loansAdded
            Note(pluralStringResource(R.plurals.import_done_loans, count, count))
        }
        if (result.loansSkipped > 0) {
            val count = result.loansSkipped
            Note(pluralStringResource(R.plurals.import_done_loans_skipped, count, count))
        }
    }
    OtiumButton(
        text = stringResource(R.string.import_done_dismiss),
        onClick = onDismiss,
        style = OtiumButtonStyle.Ghost,
        compact = true,
    )
}

/** Where to find the export in the two apps people come from. */
@Composable
private fun ImportHelp() {
    HelpItem(R.string.import_help_goodreads_title, R.string.import_help_goodreads)
    HelpItem(R.string.import_help_storygraph_title, R.string.import_help_storygraph)
}

@Composable
private fun HelpItem(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
        Note(stringResource(body))
    }
}

@Composable
private fun ExportSection(state: ImportExportUiState, actions: ImportExportActions) {
    SectionTitle(stringResource(R.string.export_heading))
    Note(stringResource(if (state.bookCount == 0) R.string.export_empty else R.string.export_note))
    OtiumButton(
        text = stringResource(R.string.export_csv_action),
        onClick = { actions.onExport(ExportFormat.CSV) },
        style = OtiumButtonStyle.Secondary,
        enabled = state.canExport,
        modifier = Modifier.fillMaxWidth(),
    )
    OtiumButton(
        text = stringResource(R.string.export_json_action),
        onClick = { actions.onExport(ExportFormat.JSON) },
        style = OtiumButtonStyle.Secondary,
        enabled = state.canExport,
        modifier = Modifier.fillMaxWidth(),
    )
    // Said once, calmly, where the choice is made; the export itself is never blocked.
    if (state.bookCount > 0) Note(stringResource(R.string.export_unencrypted))
    if (state.exporting != null) BusyIndicator(stringResource(R.string.export_in_progress))
}
