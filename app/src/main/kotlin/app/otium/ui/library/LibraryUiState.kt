package app.otium.ui.library

import androidx.compose.runtime.Immutable
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortKeys
import app.otium.domain.model.SortOrder

/** Which of the library's four faces to show. */
enum class LibraryContentKind { LOADING, EMPTY_LIBRARY, NO_RESULTS, BOOKS }

/** A shelf of books. A null [name] is the shelf for books without a category. */
@Immutable
data class Shelf(val name: String?, val books: List<BookSummary>)

/** A run of the index under one letter. A null [letter] is an ungrouped list. */
@Immutable
data class IndexGroup(val letter: String?, val books: List<BookSummary>)

/**
 * Everything the library screen shows, as one immutable value. The search text is the text the
 * user typed; [books] reflect it after a short debounce, in the chosen [sort] order.
 */
@Immutable
data class LibraryUiState(
    val query: String = "",
    val status: BookStatus? = null,
    val sort: SortOrder = SortOrder.TITLE,
    val viewMode: LibraryViewMode = LibraryViewMode.SHELF,
    val books: List<BookSummary> = emptyList(),
    /** Books in the library regardless of filters; tells an empty library from no matches. */
    val totalBooks: Int = 0,
    val isLoading: Boolean = true,
    val debugToolsAvailable: Boolean = false,
) {
    val hasActiveFilters: Boolean get() = query.isNotBlank() || status != null

    /** Status or sort differ from the defaults, which the arrange button shows. */
    val isArranged: Boolean get() = status != null || sort != SortOrder.TITLE

    val contentKind: LibraryContentKind
        get() = when {
            isLoading -> LibraryContentKind.LOADING
            totalBooks == 0 -> LibraryContentKind.EMPTY_LIBRARY
            books.isEmpty() -> LibraryContentKind.NO_RESULTS
            else -> LibraryContentKind.BOOKS
        }

    /**
     * "On the desk": every book in progress, most recently touched first. Hidden while a search
     * or a status filter is active, since the shelves below already show the matches.
     */
    val desk: List<BookSummary> by lazy {
        if (hasActiveFilters) {
            emptyList()
        } else {
            books.filter { it.status == BookStatus.IN_PROGRESS }.sortedByDescending { it.updatedAt }
        }
    }

    /** One shelf per first category, alphabetically, with uncategorized books last. */
    val shelves: List<Shelf> by lazy {
        books.groupBy { it.shelf }
            .map { (name, books) -> Shelf(name, books) }
            .sortedWith(compareBy<Shelf> { it.name == null }.thenBy { it.name?.lowercase() })
    }

    /** The index: grouped by first letter when sorted by title, otherwise one plain list. */
    val index: List<IndexGroup> by lazy {
        if (sort == SortOrder.TITLE) {
            books.groupBy {
                indexLetter(it.title)
            }.map { (letter, books) -> IndexGroup(letter, books) }
        } else {
            listOf(IndexGroup(null, books))
        }
    }
}

/** The letter a title files under; "#" for titles that start with a digit or a symbol. */
internal fun indexLetter(title: String): String {
    val first = SortKeys.title(title).firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first.isLetter()) first.toString() else "#"
}
