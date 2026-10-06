package app.otium.ui.add.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.getOrNull
import app.otium.domain.model.BookCandidate
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository
import app.otium.domain.usecase.ValidateIsbn13
import app.otium.ui.add.AddDestination
import app.otium.ui.add.emptyCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the title search screen shows. */
@Immutable
data class TitleSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<BookCandidate> = emptyList(),
    /** True after a search that found nothing, until the query changes. */
    val noResults: Boolean = false,
    val error: AppError? = null,
    val destination: AddDestination? = null,
) {
    val canSearch: Boolean get() = query.isNotBlank() && !isSearching
}

/**
 * State holder for finding an edition by title or author. Searching happens when the user asks,
 * not on every keystroke, to spare the services' quota.
 */
@HiltViewModel
class TitleSearchViewModel @Inject constructor(
    private val metadata: MetadataRepository,
    private val books: BookRepository,
) : ViewModel() {

    private val state = MutableStateFlow(TitleSearchUiState())
    val uiState: StateFlow<TitleSearchUiState> = state.asStateFlow()

    fun onQueryChange(query: String) =
        state.update { it.copy(query = query, noResults = false, error = null) }

    fun onSearch() {
        val current = state.value
        if (!current.canSearch) return
        val query = current.query.trim()
        state.update { it.copy(isSearching = true, error = null, noResults = false) }
        viewModelScope.launch {
            val result = metadata.searchByTitleAuthor(query)
            state.update {
                when (result) {
                    is Try.Success -> it.copy(
                        isSearching = false,
                        results = result.value,
                        noResults = result.value.isEmpty(),
                    )

                    is Try.Failure -> it.copy(isSearching = false, error = result.error)
                }
            }
        }
    }

    /** Opens the chosen edition on the confirm screen, or the book if it is already owned. */
    fun onResultChosen(candidate: BookCandidate) {
        viewModelScope.launch {
            val isbn13 = candidate.isbn13?.let { ValidateIsbn13(it).getOrNull() }
            val owned = isbn13?.let { books.findByIsbn13(it).getOrNull() }
            val destination = owned?.let { AddDestination.Book(it.id) }
                ?: AddDestination.Confirm(candidate)
            state.update { it.copy(destination = destination) }
        }
    }

    /** Opens an empty confirm form with the query as a starting title. */
    fun onEnterManually() = state.update {
        val typed = emptyCandidate(isbn13 = null).copy(title = it.query.trim())
        it.copy(destination = AddDestination.Confirm(typed))
    }

    fun onErrorShown() = state.update { it.copy(error = null) }

    fun onNavigated() = state.update { it.copy(destination = null) }
}
