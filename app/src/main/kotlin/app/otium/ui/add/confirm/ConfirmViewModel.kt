package app.otium.ui.add.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.map
import app.otium.domain.model.CoverChoice
import app.otium.domain.port.CapturedPhotos
import app.otium.domain.repo.BookRepository
import app.otium.domain.usecase.SaveBook
import app.otium.ui.navigation.ConfirmRoute
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
 * State holder for the confirm/edit screen: every add path ends here, and editing a book from
 * its detail screen starts here. Nothing is saved until the user taps save.
 */
@HiltViewModel
class ConfirmViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    private val saveBook: SaveBook,
    private val capturedPhotos: CapturedPhotos,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ConfirmRoute>()
    private val found = route.toCandidate()
    private val foundForm = BookForm.from(found)
    private val foundCover = found.coverUrl?.let(CoverChoice::Remote) ?: CoverChoice.None

    private val state = MutableStateFlow(
        if (route.isEdit) {
            ConfirmUiState(isEditing = true, isLoading = true)
        } else {
            ConfirmUiState(
                form = foundForm,
                cover = foundCover,
                remoteCovers = listOfNotNull(found.coverUrl) + found.alternateCoverUrls,
            )
        },
    )

    val uiState: StateFlow<ConfirmUiState> =
        combine(state, repository.observeTags()) { current, tags ->
            current.copy(knownTags = tags)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = state.value,
        )

    /**
     * Every photo picked on this screen, retakes included. A saved book has its own copy by the
     * time the screen goes, so the camera's photos are deleted then, saved or not.
     */
    private val pickedPhotos = mutableSetOf<String>()

    init {
        if (route.isEdit) loadBook()
    }

    override fun onCleared() {
        capturedPhotos.discard(pickedPhotos.toList())
    }

    fun onFormChange(form: BookForm) = state.update { it.copy(form = form) }

    fun onCoverSelected(cover: CoverChoice) {
        if (cover is CoverChoice.Picked) pickedPhotos += cover.uri
        state.update { it.copy(cover = cover) }
    }

    fun onSave() {
        val current = state.value
        if (!current.canSave) return
        state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val candidate = current.form.toCandidate(found.source, found.isbn10)
            val result = if (route.isEdit) {
                saveBook.update(route.bookId, candidate, current.cover).map { route.bookId }
            } else {
                val edited = current.form != foundForm || current.cover != foundCover
                saveBook.add(candidate, current.cover, edited)
            }
            state.update { it.afterSave(result) }
        }
    }

    fun onDuplicateDismissed() = state.update { it.copy(duplicateOf = null) }

    fun onErrorShown() = state.update { it.copy(error = null) }

    private fun ConfirmUiState.afterSave(result: Try<Long>): ConfirmUiState = when (result) {
        is Try.Success -> copy(isSaving = false, savedBookId = result.value)

        is Try.Failure -> when (val error = result.error) {
            is AppError.Duplicate -> copy(isSaving = false, duplicateOf = error.existingBookId)
            else -> copy(isSaving = false, error = error)
        }
    }

    private fun loadBook() {
        viewModelScope.launch {
            val loaded = repository.getBook(route.bookId)
            state.update { current ->
                when (loaded) {
                    is Try.Success -> current.copy(
                        isLoading = false,
                        form = BookForm.from(loaded.value),
                        cover = CoverChoice.Unchanged,
                        currentCoverPath = loaded.value.coverPath,
                    )

                    is Try.Failure -> if (loaded.error == AppError.NotFound) {
                        current.copy(isLoading = false, notFound = true)
                    } else {
                        current.copy(isLoading = false, error = loaded.error)
                    }
                }
            }
        }
    }
}
