package app.otium.ui.library

import androidx.compose.runtime.Immutable
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortOrder

/** Which of the library's four faces to show. */
enum class LibraryContentKind { LOADING, EMPTY_LIBRARY, NO_RESULTS, BOOKS }

/**
 * Everything the library screen shows, as one immutable value. The search text is the text the
 * user typed; [books] reflect it after a short debounce.
 */
@Immutable
data class LibraryUiState(
    val query: String = "",
    val status: BookStatus? = null,
    val categories: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val sort: SortOrder = SortOrder.TITLE,
    val viewMode: LibraryViewMode = LibraryViewMode.SHELF,
    val books: List<BookSummary> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    /** Books in the library regardless of filters; tells an empty library from no matches. */
    val totalBooks: Int = 0,
    val isLoading: Boolean = true,
    val debugToolsAvailable: Boolean = false,
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || status != null || categories.isNotEmpty() || tags.isNotEmpty()

    /** Category and tag selections, shown on the filters button. */
    val selectedLabelCount: Int get() = categories.size + tags.size

    val contentKind: LibraryContentKind
        get() = when {
            isLoading -> LibraryContentKind.LOADING
            totalBooks == 0 -> LibraryContentKind.EMPTY_LIBRARY
            books.isEmpty() -> LibraryContentKind.NO_RESULTS
            else -> LibraryContentKind.BOOKS
        }
}
