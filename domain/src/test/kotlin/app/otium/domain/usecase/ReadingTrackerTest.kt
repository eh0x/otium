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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTrackerTest {

    private val repo = FakeBookRepository()
    private val clock = FixedClock(now = 5_000L)
    private val tracker = ReadingTracker(repo, clock)

    // setPage

    @Test
    fun `sets the page and derives in progress`() = runTest {
        val stored = repo.seed(book(totalPages = 300))

        val result = tracker.setPage(stored.id, 120)

        assertEquals(Try.Success(Progress(120, 300, BookStatus.IN_PROGRESS)), result)
        assertEquals(Triple(stored.id, 120, 5_000L), repo.lastProgressUpdate)
    }

    @Test
    fun `page above total is clamped and the book is read`() = runTest {
        val stored = repo.seed(book(totalPages = 300))

        val result = tracker.setPage(stored.id, 999)

        assertEquals(Try.Success(Progress(300, 300, BookStatus.READ)), result)
        assertEquals(300, repo.lastProgressUpdate?.second)
    }

    @Test
    fun `negative page is clamped to zero`() = runTest {
        val stored = repo.seed(book(totalPages = 300, currentPage = 50))

        val result = tracker.setPage(stored.id, -4)

        assertEquals(Try.Success(Progress(0, 300, BookStatus.NOT_STARTED)), result)
    }

    @Test
    fun `unknown total keeps the page but stays not started`() = runTest {
        val stored = repo.seed(book(totalPages = 0))

        val result = tracker.setPage(stored.id, 40)

        assertEquals(Try.Success(Progress(40, 0, BookStatus.NOT_STARTED)), result)
    }

    @Test
    fun `setting the page of a missing book is reported and nothing is written`() = runTest {
        val result = tracker.setPage(bookId = 42L, page = 10)

        assertEquals(Try.Failure(AppError.NotFound), result)
        assertNull(repo.lastProgressUpdate)
    }

    // finish

    @Test
    fun `finishing goes to the last page and the book is read`() = runTest {
        val stored = repo.seed(book(totalPages = 250, currentPage = 10))

        val result = tracker.finish(stored.id)

        assertEquals(Try.Success(Progress(250, 250, BookStatus.READ)), result)
        assertEquals(Triple(stored.id, 250, 5_000L), repo.lastProgressUpdate)
    }

    @Test
    fun `an already finished book stays finished`() = runTest {
        val stored = repo.seed(book(totalPages = 250, currentPage = 250))

        val result = tracker.finish(stored.id)

        assertEquals(Try.Success(Progress(250, 250, BookStatus.READ)), result)
    }

    @Test
    fun `an unknown total has no last page, so nothing is written`() = runTest {
        val stored = repo.seed(book(totalPages = 0, currentPage = 0))

        val result = tracker.finish(stored.id)

        assertEquals(Try.Failure(AppError.InvalidInput("totalPages")), result)
        assertNull(repo.lastProgressUpdate)
    }

    @Test
    fun `finishing a missing book is reported and nothing is written`() = runTest {
        val result = tracker.finish(bookId = 42L)

        assertEquals(Try.Failure(AppError.NotFound), result)
        assertNull(repo.lastProgressUpdate)
    }

    // setTotalPages

    @Test
    fun `sets the total and derives the status from the current page`() = runTest {
        val stored = repo.seed(book(totalPages = 0, currentPage = 40))

        val result = tracker.setTotalPages(stored.id, totalPages = 200)

        assertEquals(Try.Success(Progress(40, 200, BookStatus.IN_PROGRESS)), result)
        assertEquals(200, repo.all.single().totalPages)
    }

    @Test
    fun `a smaller total clamps the current page and finishes the book`() = runTest {
        val stored = repo.seed(book(totalPages = 300, currentPage = 250))

        val result = tracker.setTotalPages(stored.id, totalPages = 120)

        assertEquals(Try.Success(Progress(120, 120, BookStatus.READ)), result)
        assertEquals(120, repo.all.single().currentPage)
    }

    @Test
    fun `a new total is stamped and protected from metadata refresh`() = runTest {
        val stored = repo.seed(book(totalPages = 0))

        tracker.setTotalPages(stored.id, totalPages = 150)

        val saved = repo.all.single()
        assertEquals(5_000L, saved.updatedAt)
        assertTrue(saved.userEdited)
    }

    @Test
    fun `a total that is not positive is rejected and nothing changes`() = runTest {
        val stored = repo.seed(book(totalPages = 300, currentPage = 10))

        for (invalid in listOf(0, -5)) {
            assertEquals(
                Try.Failure(AppError.InvalidInput("totalPages")),
                tracker.setTotalPages(stored.id, totalPages = invalid),
            )
        }

        val saved = repo.all.single()
        assertEquals(300, saved.totalPages)
        assertFalse(saved.userEdited)
    }

    @Test
    fun `setting the total of a missing book is reported`() = runTest {
        val result = tracker.setTotalPages(bookId = 77L, totalPages = 100)

        assertEquals(Try.Failure(AppError.NotFound), result)
    }
}
