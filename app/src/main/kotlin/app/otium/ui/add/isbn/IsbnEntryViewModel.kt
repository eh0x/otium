package app.otium.ui.add.isbn

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.getOrNull
import app.otium.domain.error.map
import app.otium.domain.usecase.AddBookFromIsbn
import app.otium.domain.usecase.SaveIsbnForLater
import app.otium.domain.usecase.ValidateIsbn13
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

private const val ISBN10_LENGTH = 10
private const val ISBN13_LENGTH = 13

/**
 * Everything the ISBN entry screen shows. [destination] is where to go next; the screen reports
 * it handled with `onNavigated`.
 */
@Immutable
data class IsbnEntryUiState(
    val input: String = "",
    val isLooking: Boolean = false,
    val lookupError: AppError? = null,
    val destination: AddDestination? = null,
) {
    /** The input as a normalized ISBN-13, or null while it is not a valid ISBN. */
    val isbn13: String? get() = ValidateIsbn13(input).getOrNull()

    /** Set only once the input is long enough to judge, so typing is not interrupted. */
    val inputError: AppError?
        get() {
            val length = input.count { it.isLetterOrDigit() }
            val judged = length == ISBN10_LENGTH || length >= ISBN13_LENGTH
            return if (judged && isbn13 == null) AppError.InvalidIsbn(input) else null
        }

    val canLookUp: Boolean get() = isbn13 != null && !isLooking

    /** After the services could not be reached, the ISBN can be kept and looked up later. */
    val canSaveForLater: Boolean
        get() = lookupError?.isTransient == true && isbn13 != null && !isLooking
}

/** State holder for typing an ISBN and looking it up. */
@HiltViewModel
class IsbnEntryViewModel @Inject constructor(
    private val addBookFromIsbn: AddBookFromIsbn,
    private val saveIsbnForLater: SaveIsbnForLater,
) : ViewModel() {

    private val state = MutableStateFlow(IsbnEntryUiState())
    val uiState: StateFlow<IsbnEntryUiState> = state.asStateFlow()

    fun onInputChange(input: String) = state.update { it.copy(input = input, lookupError = null) }

    fun onLookUp() {
        val isbn13 = state.value.isbn13 ?: return
        if (state.value.isLooking) return
        run(isbn13) { addBookFromIsbn(isbn13).toDestination(isbn13) }
    }

    fun onSaveForLater() {
        val isbn13 = state.value.isbn13 ?: return
        run(isbn13) {
            when (val saved = saveIsbnForLater(isbn13)) {
                is Try.Success -> saved.map { AddDestination.Book(it) }

                is Try.Failure -> (saved.error as? AppError.Duplicate)
                    ?.let { Try.Success(AddDestination.Book(it.existingBookId)) }
                    ?: saved
            }
        }
    }

    fun onEnterManually() = state.update {
        it.copy(destination = AddDestination.Confirm(emptyCandidate(it.isbn13 ?: it.input.trim())))
    }

    fun onNavigated() = state.update { it.copy(destination = null) }

    private fun run(isbn13: String, action: suspend () -> Try<AddDestination>) {
        state.update { it.copy(isLooking = true, lookupError = null) }
        viewModelScope.launch {
            val result = action()
            state.update { current ->
                // The user may have changed the ISBN meanwhile; a stale answer is dropped.
                if (current.isbn13 != isbn13) return@update current.copy(isLooking = false)
                when (result) {
                    is Try.Success -> current.copy(isLooking = false, destination = result.value)
                    is Try.Failure -> current.copy(isLooking = false, lookupError = result.error)
                }
            }
        }
    }
}
