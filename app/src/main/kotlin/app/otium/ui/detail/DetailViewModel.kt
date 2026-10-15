package app.otium.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.NoteRepository
import app.otium.domain.usecase.ReadingTracker
import app.otium.domain.usecase.SaveNote
import app.otium.ui.navigation.DetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** State holder for one book. The book is read live, so every change shows without refresh. */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val reading: ReadingTracker,
    private val noteRepository: NoteRepository,
    private val saveNote: SaveNote,
) : ViewModel() {

    private val bookId: Long = savedStateHandle.toRoute<DetailRoute>().bookId

    private data class Outcome(val deleted: Boolean = false, val error: AppError? = null)

    private val outcome = MutableStateFlow(Outcome())

    /** Paired with a flag: true until the first read, which tells "loading" from "not found". */
    private val book = repository.observeBook(bookId)
        .map<Book?, Pair<Book?, Boolean>> { it to false }
        .onStart { emit(null to true) }

    val uiState: StateFlow<DetailUiState> = combine(
        book,
        noteRepository.observeNotes(bookId),
        outcome,
    ) { (loaded, loading), notes, result ->
        DetailUiState(
            isLoading = loading,
            book = loaded,
            notes = notes,
            deleted = result.deleted,
            error = result.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DetailUiState(),
    )

    fun onProgressConfirmed(page: Int) = perform { reading.setPage(bookId, page) }

    fun onMarkFinished() = perform { reading.finish(bookId) }

    fun onTotalPagesConfirmed(totalPages: Int) = perform {
        reading.setTotalPages(bookId, totalPages)
    }

    fun onDelete() {
        viewModelScope.launch {
            when (val result = repository.delete(bookId)) {
                is Try.Success -> outcome.update { it.copy(deleted = true) }
                is Try.Failure -> outcome.update { it.copy(error = result.error) }
            }
        }
    }

    /** Adds the note, or changes the one being edited. */
    fun onNoteSaved(draft: NoteDraft) = perform {
        val editing = draft.editing
        if (editing == null) {
            saveNote.add(bookId, draft.kind, draft.text, draft.page)
        } else {
            saveNote.update(editing, draft.kind, draft.text, draft.page)
        }
    }

    fun onNoteDeleted(id: Long) = perform { noteRepository.delete(id) }

    fun onErrorShown() = outcome.update { it.copy(error = null) }

    private fun perform(action: suspend () -> Try<*>) {
        viewModelScope.launch {
            val result = action()
            if (result is Try.Failure) outcome.update { it.copy(error = result.error) }
        }
    }
}
