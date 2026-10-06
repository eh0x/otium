package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookStatus
import app.otium.domain.model.Progress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetTotalPagesTest {

    private val repo = FakeBookRepository()
    private val clock = FixedClock(now = 9_000L)
    private val setTotalPages = SetTotalPages(repo, clock)

    @Test
    fun `sets the total and derives the status from the current page`() = runTest {
        val stored = repo.seed(book(totalPages = 0, currentPage = 40))

        val result = setTotalPages(stored.id, totalPages = 200)

        assertEquals(Try.Success(Progress(40, 200, BookStatus.IN_PROGRESS)), result)
        assertEquals(200, repo.all.single().totalPages)
    }

    @Test
    fun `a smaller total clamps the current page and finishes the book`() = runTest {
        val stored = repo.seed(book(totalPages = 300, currentPage = 250))

        val result = setTotalPages(stored.id, totalPages = 120)

        assertEquals(Try.Success(Progress(120, 120, BookStatus.READ)), result)
        assertEquals(120, repo.all.single().currentPage)
    }

    @Test
    fun `the change is stamped and protected from metadata refresh`() = runTest {
        val stored = repo.seed(book(totalPages = 0))

        setTotalPages(stored.id, totalPages = 150)

        val saved = repo.all.single()
        assertEquals(9_000L, saved.updatedAt)
        assertTrue(saved.userEdited)
    }

    @Test
    fun `a total that is not positive is rejected and nothing changes`() = runTest {
        val stored = repo.seed(book(totalPages = 300, currentPage = 10))

        for (invalid in listOf(0, -5)) {
            assertEquals(
                Try.Failure(AppError.InvalidInput("totalPages")),
                setTotalPages(stored.id, totalPages = invalid),
            )
        }

        val saved = repo.all.single()
        assertEquals(300, saved.totalPages)
        assertFalse(saved.userEdited)
    }

    @Test
    fun `a missing book is reported`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), setTotalPages(bookId = 77L, totalPages = 100))
    }
}
