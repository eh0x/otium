package app.otium.domain.usecase

import app.otium.domain.model.BookStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DeriveStatusTest {

    @Test
    fun `page zero is not started`() {
        assertEquals(BookStatus.NOT_STARTED, DeriveStatus(currentPage = 0, totalPages = 300))
    }

    @Test
    fun `page between zero and total is in progress`() {
        assertEquals(BookStatus.IN_PROGRESS, DeriveStatus(currentPage = 1, totalPages = 300))
        assertEquals(BookStatus.IN_PROGRESS, DeriveStatus(currentPage = 299, totalPages = 300))
    }

    @Test
    fun `page equal to total is read`() {
        assertEquals(BookStatus.READ, DeriveStatus(currentPage = 300, totalPages = 300))
    }

    @Test
    fun `page above total is read`() {
        assertEquals(BookStatus.READ, DeriveStatus(currentPage = 350, totalPages = 300))
    }

    @Test
    fun `unknown total pages is not started even with a page set`() {
        assertEquals(BookStatus.NOT_STARTED, DeriveStatus(currentPage = 0, totalPages = 0))
        assertEquals(BookStatus.NOT_STARTED, DeriveStatus(currentPage = 40, totalPages = 0))
    }

    @Test
    fun `negative values are not started`() {
        assertEquals(BookStatus.NOT_STARTED, DeriveStatus(currentPage = -5, totalPages = 300))
        assertEquals(BookStatus.NOT_STARTED, DeriveStatus(currentPage = 10, totalPages = -1))
    }
}
