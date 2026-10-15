package app.otium.ui.library

import androidx.compose.runtime.Immutable
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SeriesProgress
import app.otium.domain.model.SortKeys
import app.otium.domain.model.SortOrder

/** Which of the library's four faces to show. */
enum class LibraryContentKind { LOADING, EMPTY_LIBRARY, NO_RESULTS, BOOKS }

/**
 * A shelf of books: a category, or a series when the library is grouped by series. A null [name]
 * is the shelf for books without a category, or outside any series. [series] is the progress
 * through a series shelf.
 */
@Immutable
data class Shelf(
    val name: String?,
    val books: List<BookSummary>,
    val series: SeriesProgress? = null,
)

/** What a run of the index stands under. */
@Immutable
sealed interface GroupHeading {
    data class Letter(val letter: String) : GroupHeading

    data class Series(val progress: SeriesProgress) : GroupHeading

    data object NoSeries : GroupHeading
}

/** A run of the index under one heading. A null [heading] is an ungrouped list. */
@Immutable
data class IndexGroup(val heading: GroupHeading?, val books: List<BookSummary>)

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
    /** The series the library is narrowed to, or null for every book. */
    val series: String? = null,
    /** Every series in the library with progress through it, whatever the filters. */
    val knownSeries: List<SeriesProgress> = emptyList(),
) {
    val hasActiveFilters: Boolean get() = query.isNotBlank() || status != null || series != null

    /** Status, series or sort differ from the defaults, which the arrange button shows. */
    val isArranged: Boolean get() = status != null || series != null || sort != SortOrder.TITLE

    /** Shelves and the index stand under series instead of categories and letters. */
    val groupsBySeries: Boolean get() = sort == SortOrder.SERIES

    /**
     * Progress through the chosen series, shown above the books. Null when no series is chosen,
     * or when the books are grouped by series and its group shows it already.
     */
    val seriesSummary: SeriesProgress?
        get() = series?.takeUnless { groupsBySeries }
            ?.let { name -> progressOf(name, books.filter { it.series == name }) }

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

    /**
     * One shelf per first category, alphabetically, with uncategorized books last. Grouped by
     * series, one shelf per series in the books' order, with books outside a series last.
     */
    val shelves: List<Shelf> by lazy {
        if (groupsBySeries) {
            bySeries.map { (name, books) ->
                Shelf(name, books, name?.let { progressOf(it, books) })
            }
        } else {
            books.groupBy { it.shelf }
                .map { (name, books) -> Shelf(name, books) }
                .sortedWith(compareBy<Shelf> { it.name == null }.thenBy { it.name?.lowercase() })
        }
    }

    /**
     * The index: grouped by first letter when sorted by title, by series when sorted by series,
     * otherwise one plain list.
     */
    val index: List<IndexGroup> by lazy {
        when (sort) {
            SortOrder.TITLE -> books.groupBy { indexLetter(it.title) }
                .map { (letter, books) -> IndexGroup(GroupHeading.Letter(letter), books) }

            SortOrder.SERIES -> bySeries.map { (name, books) ->
                val heading = name?.let { GroupHeading.Series(progressOf(it, books)) }
                IndexGroup(heading ?: GroupHeading.NoSeries, books)
            }

            else -> listOf(IndexGroup(null, books))
        }
    }

    /** Runs of books by series, in the books' order, which puts books outside a series last. */
    private val bySeries: Map<String?, List<BookSummary>> by lazy { books.groupBy { it.series } }

    /**
     * Progress through the whole series, which the shown [books] alone may not give: a filter
     * can hide some of its books. Falls back to the shown books until the series list arrives.
     */
    private fun progressOf(name: String, shown: List<BookSummary>): SeriesProgress =
        knownSeries.firstOrNull { it.name == name } ?: SeriesProgress.of(name, shown)
}

/** The letter a title files under; "#" for titles that start with a digit or a symbol. */
internal fun indexLetter(title: String): String {
    val first = SortKeys.title(title).firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first.isLetter()) first.toString() else "#"
}
