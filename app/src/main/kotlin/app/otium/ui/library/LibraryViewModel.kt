package app.otium.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortOrder
import app.otium.domain.port.LibraryPreferences
import app.otium.domain.repo.BookRepository
import app.otium.domain.usecase.ObserveLibrary
import app.otium.ui.debug.DebugTools
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val QUERY_DEBOUNCE_MILLIS = 200L
private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * State holder for the library screen. It owns what the user has chosen (search text, status,
 * sort) and turns it into one [LibraryFilter] for the database. It holds no business rules:
 * filtering and ordering happen in the query, and reads that need no rule go straight to the
 * repository.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val observeLibrary: ObserveLibrary,
    repository: BookRepository,
    private val preferences: LibraryPreferences,
    private val debugTools: DebugTools,
) : ViewModel() {

    private data class Controls(
        val query: String = "",
        val status: BookStatus? = null,
        val sort: SortOrder = SortOrder.TITLE,
    )

    private val controls = MutableStateFlow(Controls())

    /** Search text is debounced so typing does not query on every key; clearing it is instant. */
    @OptIn(FlowPreview::class)
    private val filter: Flow<LibraryFilter> = combine(
        controls.map { it.query }
            .debounce { query -> if (query.isBlank()) 0L else QUERY_DEBOUNCE_MILLIS }
            .distinctUntilChanged(),
        controls,
    ) { debouncedQuery, current ->
        LibraryFilter(
            query = debouncedQuery,
            status = current.status,
            sort = current.sort,
        )
    }.distinctUntilChanged()

    /** Null until the first result, which is how "loading" is told from "empty". */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val books: Flow<List<BookSummary>?> = filter
        .flatMapLatest { observeLibrary(it) }
        .map<List<BookSummary>, List<BookSummary>?> { it }
        .onStart { emit(null) }

    val uiState: StateFlow<LibraryUiState> = combine(
        controls,
        books,
        preferences.viewMode,
        repository.observeCount(),
    ) { current, loaded, viewMode, count ->
        LibraryUiState(
            query = current.query,
            status = current.status,
            sort = current.sort,
            viewMode = viewMode,
            books = loaded.orEmpty(),
            totalBooks = count,
            isLoading = loaded == null,
            debugToolsAvailable = debugTools.isAvailable,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LibraryUiState(debugToolsAvailable = debugTools.isAvailable),
    )

    fun onQueryChange(query: String) = controls.update { it.copy(query = query) }

    fun onStatusSelected(status: BookStatus?) = controls.update { it.copy(status = status) }

    fun onSortSelected(sort: SortOrder) = controls.update { it.copy(sort = sort) }

    /** Clears search and the status filter. Sort order and view mode are presentation, so they stay. */
    fun onClearFilters() = controls.update { Controls(sort = it.sort) }

    fun onViewModeSelected(mode: LibraryViewMode) {
        viewModelScope.launch { preferences.setViewMode(mode) }
    }

    fun onSeedDebugData() {
        viewModelScope.launch { debugTools.seedLibrary() }
    }
}
