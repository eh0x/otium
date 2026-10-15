package app.otium.ui.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.SortOrder
import app.otium.domain.repo.BookRepository
import app.otium.domain.usecase.ReadingTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val FINISHED_SHOWN = 5

/** Pages added by the one-tap button on each open book. */
internal const val QUICK_PAGES = 10

/**
 * State holder for the Reading tab. It reads the same library as the shelves, filtered to books
 * in progress and books finished; nothing new is stored.
 */
@HiltViewModel
class ReadingViewModel @Inject constructor(
    books: BookRepository,
    private val reading: ReadingTracker,
) : ViewModel() {

    private val error = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<ReadingUiState> = combine(
        books.observeLibrary(
            LibraryFilter(status = BookStatus.IN_PROGRESS, sort = SortOrder.LAST_UPDATED),
        ),
        books.observeLibrary(
            LibraryFilter(status = BookStatus.READ, sort = SortOrder.LAST_UPDATED),
        ),
        error,
    ) { open, finished, failure ->
        ReadingUiState(
            isLoading = false,
            open = open,
            finished = finished.take(FINISHED_SHOWN),
            error = failure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ReadingUiState(),
    )

    /** Moves [book] on by [QUICK_PAGES]; the use case keeps it within the page count. */
    fun onAddPages(book: BookSummary) {
        viewModelScope.launch {
            val result = reading.setPage(book.id, book.currentPage + QUICK_PAGES)
            if (result is Try.Failure) error.value = result.error
        }
    }

    fun onErrorShown() {
        error.value = null
    }
}
