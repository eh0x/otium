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
import org.junit.Test

class UpdateProgressTest {

    private val repo = FakeBookRepository()
    private val clock = FixedClock(now = 5_000L)
    private val updateProgress = UpdateProgress(repo, clock)

    @Test
    fun `sets the page and derives in progress`() = runTest {
        val stored = repo.seed(book(totalPages = 300))

        val result = updateProgress(stored.id, 120)

        assertEquals(Try.Success(Progress(120, 300, BookStatus.IN_PROGRESS)), result)
        assertEquals(Triple(stored.id, 120, 5_000L), repo.lastProgressUpdate)
    }

    @Test
    fun `page above total is clamped and the book is read`() = runTest {
        val stored = repo.seed(book(totalPages = 300))

        val result = updateProgress(stored.id, 999)

        assertEquals(Try.Success(Progress(300, 300, BookStatus.READ)), result)
        assertEquals(300, repo.lastProgressUpdate?.second)
    }

    @Test
    fun `negative page is clamped to zero`() = runTest {
        val stored = repo.seed(book(totalPages = 300, currentPage = 50))

        val result = updateProgress(stored.id, -4)

        assertEquals(Try.Success(Progress(0, 300, BookStatus.NOT_STARTED)), result)
    }

    @Test
    fun `unknown total keeps the page but stays not started`() = runTest {
        val stored = repo.seed(book(totalPages = 0))

        val result = updateProgress(stored.id, 40)

        assertEquals(Try.Success(Progress(40, 0, BookStatus.NOT_STARTED)), result)
    }

    @Test
    fun `missing book is reported and nothing is written`() = runTest {
        val result = updateProgress(bookId = 42L, page = 10)

        assertEquals(Try.Failure(AppError.NotFound), result)
        assertEquals(null, repo.lastProgressUpdate)
    }
}
