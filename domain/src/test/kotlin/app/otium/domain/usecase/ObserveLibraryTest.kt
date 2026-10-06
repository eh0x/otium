package app.otium.domain.usecase

import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveLibraryTest {

    private val repo = FakeBookRepository()
    private val observeLibrary = ObserveLibrary(repo)

    @Test
    fun `search text is trimmed and whitespace collapsed`() {
        observeLibrary(LibraryFilter(query = "  the   hobbit \n"))

        assertEquals("the hobbit", repo.lastFilter?.query)
    }

    @Test
    fun `other filter fields pass through untouched`() {
        val filter = LibraryFilter(
            query = "x",
            status = BookStatus.READ,
            categories = setOf("Fiction"),
            tags = setOf("favourite"),
            author = "Tolkien",
            sort = SortOrder.PROGRESS,
        )

        observeLibrary(filter)

        assertEquals(filter, repo.lastFilter)
    }
}
