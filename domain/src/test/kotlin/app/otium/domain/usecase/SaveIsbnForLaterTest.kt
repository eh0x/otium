package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveIsbnForLaterTest {

    private val repo = FakeBookRepository()
    private var scheduled = 0
    private val saveForLater = SaveIsbnForLater(repo, FixedClock(now = 9_000L)) { scheduled++ }

    @Test
    fun `saving plans a lookup for later`() = runTest {
        saveForLater("9780306406157")

        assertEquals(1, scheduled)
    }

    @Test
    fun `nothing is planned when nothing was saved`() = runTest {
        saveForLater("123")

        assertEquals(0, scheduled)
    }

    @Test
    fun `saves a placeholder that waits for a lookup`() = runTest {
        val result = saveForLater("978-0-306-40615-7")

        val id = (result as Try.Success).value
        val saved = repo.all.single { it.id == id }
        assertEquals("9780306406157", saved.isbn13)
        assertEquals(
            "the ISBN stands in for the title until the lookup",
            "9780306406157",
            saved.title,
        )
        assertTrue(saved.needsLookup)
        assertFalse(saved.userEdited)
        assertEquals(BookSource.MANUAL, saved.source)
        assertEquals(0, saved.totalPages)
        assertEquals(9_000L, saved.addedAt)
    }

    @Test
    fun `an invalid isbn is rejected`() = runTest {
        assertEquals(Try.Failure(AppError.InvalidIsbn("123")), saveForLater("123"))
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `an isbn already in the library reports the existing book`() = runTest {
        val existing = repo.seed(book(isbn13 = "9780306406157"))

        assertEquals(Try.Failure(AppError.Duplicate(existing.id)), saveForLater("9780306406157"))
        assertEquals(1, repo.all.size)
    }
}
