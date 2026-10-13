package app.otium.ui.library

import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.SeriesProgress
import app.otium.domain.model.SortKeys
import app.otium.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUiStateTest {

    private fun book(
        id: Long,
        title: String,
        series: String? = null,
        seriesIndex: Double? = null,
        shelf: String? = null,
    ) = BookSummary(
        id = id,
        title = title,
        authors = "Author",
        coverPath = null,
        coverSourceUrl = null,
        currentPage = 0,
        totalPages = 100,
        status = BookStatus.NOT_STARTED,
        shelf = shelf,
        series = series,
        seriesIndex = seriesIndex,
    )

    private val library = listOf(
        book(1, "Emma", shelf = "Classics"),
        book(2, "Leviathan Wakes", "The Expanse", 1.0, shelf = "Science fiction"),
        book(3, "Dune", "Dune", 1.0, shelf = "Science fiction"),
        book(4, "Dune Messiah", "Dune", 2.0, shelf = "Science fiction"),
    ).sortedWith(SortKeys.bySeries)

    private val dune = SeriesProgress("Dune", books = 3, read = 1, fraction = 0.5)

    private fun state(sort: SortOrder = SortOrder.SERIES, series: String? = null) = LibraryUiState(
        books = library,
        sort = sort,
        series = series,
        knownSeries = listOf(dune),
        totalBooks = library.size,
        isLoading = false,
    )

    @Test
    fun `sorted by series, the index has one group per series and books outside one last`() {
        val groups = state().index

        assertEquals(
            listOf(
                GroupHeading.Series(dune),
                GroupHeading.Series(SeriesProgress("The Expanse", 1, 0, 0.0)),
                GroupHeading.NoSeries,
            ),
            groups.map { it.heading },
        )
        assertEquals(listOf("Dune", "Dune Messiah"), groups[0].books.map { it.title })
        assertEquals(listOf("Emma"), groups[2].books.map { it.title })
    }

    @Test
    fun `a series header shows progress through the whole series, not only the books shown`() {
        val heading = state().index.first().heading as GroupHeading.Series

        assertEquals(3, heading.progress.books)
        assertEquals(1, heading.progress.read)
    }

    @Test
    fun `sorted by series, the shelves are series too`() {
        val shelves = state().shelves

        assertEquals(listOf("Dune", "The Expanse", null), shelves.map { it.name })
        assertEquals(dune, shelves.first().series)
        assertTrue(state().groupsBySeries)
    }

    @Test
    fun `other sorts keep category shelves and letter headings`() {
        val byTitle = state(sort = SortOrder.TITLE)

        assertEquals(listOf("Classics", "Science fiction"), byTitle.shelves.map { it.name })
        assertNull(byTitle.shelves.first().series)
        assertTrue(byTitle.index.all { it.heading is GroupHeading.Letter })
        assertFalse(byTitle.groupsBySeries)
    }

    @Test
    fun `a chosen series is a filter that hides the desk and shows its progress`() {
        val filtered = state(sort = SortOrder.TITLE, series = "Dune")

        assertTrue(filtered.hasActiveFilters)
        assertTrue(filtered.isArranged)
        assertEquals(dune, filtered.seriesSummary)
    }

    @Test
    fun `grouped by series, the chosen series shows its progress in its group instead`() {
        assertNull(state(series = "Dune").seriesSummary)
    }
}
