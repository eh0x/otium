package app.otium.ui.add.scan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.BarcodeScanner
import app.otium.domain.usecase.AddBookFromIsbn
import app.otium.domain.usecase.SaveIsbnForLater
import app.otium.ui.add.AddDestination
import app.otium.ui.add.emptyCandidate
import app.otium.ui.add.isTransient
import app.otium.ui.add.toDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the scanner is doing. Only [SCANNING] accepts a new read. */
enum class ScanPhase { SCANNING, LOOKING_UP, FAILED }

/**
 * Everything the scan screen shows. [reads] counts accepted reads, so the screen can give one
 * haptic tick per read; [destination] is where to go next until the screen reports it handled.
 */
@Immutable
data class ScanUiState(
    val phase: ScanPhase = ScanPhase.SCANNING,
    val reads: Int = 0,
    val isbn13: String? = null,
    val error: AppError? = null,
    val destination: AddDestination? = null,
) {
    val canSaveForLater: Boolean
        get() = phase == ScanPhase.FAILED && error?.isTransient == true && isbn13 != null
}

/**
 * State holder for adding a book by its barcode. The first read is looked up at once; further
 * reads are ignored until that one is handled, so one book never becomes two lookups.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    scanner: BarcodeScanner,
    private val addBookFromIsbn: AddBookFromIsbn,
    private val saveIsbnForLater: SaveIsbnForLater,
) : ViewModel() {

    private val state = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            scanner.reads.collect { isbn13 ->
                if (state.value.phase == ScanPhase.SCANNING) lookUp(isbn13)
            }
        }
    }

    fun onScanAgain() = state.update {
        it.copy(phase = ScanPhase.SCANNING, isbn13 = null, error = null)
    }

    fun onSaveForLater() {
        val isbn13 = state.value.isbn13 ?: return
        state.update { it.copy(phase = ScanPhase.LOOKING_UP, error = null) }
        viewModelScope.launch {
            val saved = saveIsbnForLater(isbn13)
            state.update { current ->
                when (saved) {
                    is Try.Success -> current.copy(destination = AddDestination.Book(saved.value))

                    is Try.Failure -> when (val error = saved.error) {
                        is AppError.Duplicate ->
                            current.copy(destination = AddDestination.Book(error.existingBookId))

                        else -> current.copy(phase = ScanPhase.FAILED, error = error)
                    }
                }
            }
        }
    }

    fun onEnterManually() = state.update {
        it.copy(destination = AddDestination.Confirm(emptyCandidate(it.isbn13)))
    }

    /** The screen has moved on. Coming back to it starts a fresh scan. */
    fun onNavigated() = state.update {
        it.copy(destination = null, phase = ScanPhase.SCANNING, isbn13 = null, error = null)
    }

    private fun lookUp(isbn13: String) {
        state.update {
            it.copy(
                phase = ScanPhase.LOOKING_UP,
                reads = it.reads + 1,
                isbn13 = isbn13,
                error = null,
            )
        }
        viewModelScope.launch {
            val result = addBookFromIsbn(isbn13).toDestination(isbn13)
            state.update {
                when (result) {
                    is Try.Success -> it.copy(destination = result.value)
                    is Try.Failure -> it.copy(phase = ScanPhase.FAILED, error = result.error)
                }
            }
        }
    }
}
