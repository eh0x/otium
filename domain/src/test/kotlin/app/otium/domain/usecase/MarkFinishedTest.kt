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
import org.junit.Assert.assertNull
import org.junit.Test

class MarkFinishedTest {

    private val repo = FakeBookRepository()
    private val clock = FixedClock(now = 5_000L)
    private val markFinished = MarkFinished(repo, UpdateProgress(repo, clock))

    @Test
    fun `goes to the last page and the book is read`() = runTest {
        val stored = repo.seed(book(totalPages = 250, currentPage = 10))

        val result = markFinished(stored.id)

        assertEquals(Try.Success(Progress(250, 250, BookStatus.READ)), result)
        assertEquals(Triple(stored.id, 250, 5_000L), repo.lastProgressUpdate)
    }

    @Test
    fun `an already finished book stays finished`() = runTest {
        val stored = repo.seed(book(totalPages = 250, currentPage = 250))

        val result = markFinished(stored.id)

        assertEquals(Try.Success(Progress(250, 250, BookStatus.READ)), result)
    }

    @Test
    fun `an unknown total has no last page, so nothing is written`() = runTest {
        val stored = repo.seed(book(totalPages = 0, currentPage = 0))

        val result = markFinished(stored.id)

        assertEquals(Try.Failure(AppError.InvalidInput("totalPages")), result)
        assertNull(repo.lastProgressUpdate)
    }

    @Test
    fun `a missing book is reported and nothing is written`() = runTest {
        val result = markFinished(bookId = 42L)

        assertEquals(Try.Failure(AppError.NotFound), result)
        assertNull(repo.lastProgressUpdate)
    }
}
