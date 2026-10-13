package app.otium.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesProgressTest {

    private fun book(currentPage: Int, totalPages: Int) = BookSummary(
        id = 1L,
        title = "Title",
        authors = "",
        coverPath = null,
        coverSourceUrl = null,
        currentPage = currentPage,
        totalPages = totalPages,
        status = BookStatus.NOT_STARTED,
    )

    @Test
    fun `counts read books and averages each book's progress`() {
        val progress = SeriesProgress.of(
            "Dune",
            listOf(book(400, 400), book(100, 200), book(0, 300), book(50, 0)),
        )

        assertEquals(SeriesProgress(name = "Dune", books = 4, read = 1, fraction = 0.375), progress)
    }

    @Test
    fun `a page past the end counts as the whole book`() {
        assertEquals(1.0, SeriesProgress.of("Dune", listOf(book(500, 400))).fraction, 0.0)
    }

    @Test
    fun `a series without books has no progress`() {
        assertEquals(SeriesProgress("Dune", 0, 0, 0.0), SeriesProgress.of("Dune", emptyList()))
    }
}
