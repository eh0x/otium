package app.otium.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.domain.error.Try
import app.otium.domain.repo.BookRepository
import app.otium.domain.transfer.ExportFormat
import app.otium.domain.usecase.ExportLibrary
import app.otium.domain.usecase.ImportLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * State holder for the import and export screen. An import is two steps: [onFilePicked] reads the
 * file and shows what it would add, [onImportConfirmed] adds it. One thing runs at a time; a tap
 * while something runs is ignored. Leaving the screen stops an import between books: what was
 * added stays, and importing the file again skips it.
 */
@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val importLibrary: ImportLibrary,
    private val exportLibrary: ExportLibrary,
    books: BookRepository,
) : ViewModel() {

    private val state = MutableStateFlow(ImportExportUiState())

    val uiState: StateFlow<ImportExportUiState> = combine(state, books.observeCount()) { s, count ->
        s.copy(bookCount = count)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        ImportExportUiState(),
    )

    /** The owner chose [uri] in the file picker, or cancelled it (null). */
    fun onFilePicked(uri: String?) {
        if (uri == null || state.value.isBusy) return
        state.update { it.copy(step = ImportStep.Reading) }
        viewModelScope.launch {
            when (val result = importLibrary.preview(uri)) {
                is Try.Success -> state.update {
                    it.copy(step = ImportStep.Previewing(result.value))
                }

                is Try.Failure -> state.update {
                    it.copy(step = ImportStep.Idle, error = result.error)
                }
            }
        }
    }

    fun onImportConfirmed() {
        val preview = (state.value.step as? ImportStep.Previewing)?.preview ?: return
        state.update { it.copy(step = ImportStep.Importing(done = 0, total = preview.rows.size)) }
        viewModelScope.launch {
            val result = importLibrary.import(preview) { done, total ->
                state.update { it.copy(step = ImportStep.Importing(done, total)) }
            }
            state.update { it.copy(step = ImportStep.Done(result)) }
        }
    }

    fun onImportDismissed() {
        if (state.value.step is ImportStep.Previewing) {
            state.update {
                it.copy(step = ImportStep.Idle)
            }
        }
    }

    fun onResultDismissed() {
        if (state.value.step is ImportStep.Done) state.update { it.copy(step = ImportStep.Idle) }
    }

    /** The owner created [uri] for an export in [format], or cancelled (null). */
    fun onExport(uri: String?, format: ExportFormat) {
        if (uri == null || state.value.isBusy) return
        state.update { it.copy(exporting = format) }
        viewModelScope.launch {
            val result = exportLibrary(uri, format)
            state.update {
                when (result) {
                    is Try.Success -> it.copy(exporting = null, exported = result.value)
                    is Try.Failure -> it.copy(exporting = null, error = result.error)
                }
            }
        }
    }

    fun onExportedShown() = state.update { it.copy(exported = null) }

    fun onErrorShown() = state.update { it.copy(error = null) }
}
