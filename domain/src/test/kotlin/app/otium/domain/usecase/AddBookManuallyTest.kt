package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddBookManuallyTest {

    private val repo = FakeBookRepository()
    private val clock = FixedClock(now = 7_000L)
    private val addBook = AddBookManually(repo, clock)

    private fun candidate(
        title: String = "Dune",
        isbn13: String? = "9780306406157",
        isbn10: String? = null,
        source: BookSource = BookSource.MANUAL,
    ) = BookCandidate(
        isbn13 = isbn13,
        isbn10 = isbn10,
        title = title,
        authors = listOf("Frank Herbert"),
        totalPages = 412,
        tags = listOf("scifi"),
        source = source,
    )

    @Test
    fun `saves a valid candidate with timestamps and zero progress`() = runTest {
        val result = addBook(candidate())

        val id = (result as Try.Success).value
        val saved = repo.all.single { it.id == id }
        assertEquals("Dune", saved.title)
        assertEquals(412, saved.totalPages)
        assertEquals(0, saved.currentPage)
        assertEquals(7_000L, saved.addedAt)
        assertEquals(7_000L, saved.updatedAt)
        assertEquals(listOf("scifi"), saved.tags)
        assertFalse(saved.needsLookup)
    }

    @Test
    fun `blank title is rejected`() = runTest {
        val result = addBook(candidate(title = "   "))

        assertEquals(Try.Failure(AppError.InvalidInput("title")), result)
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `invalid isbn is rejected`() = runTest {
        val result = addBook(candidate(isbn13 = "9780306406158"))

        assertEquals(Try.Failure(AppError.InvalidIsbn("9780306406158")), result)
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `book without an isbn can be saved`() = runTest {
        val result = addBook(candidate(isbn13 = null))

        assertTrue(result is Try.Success)
        assertEquals(null, repo.all.single().isbn13)
    }

    @Test
    fun `isbn10 input is converted and both forms are stored`() = runTest {
        val result = addBook(candidate(isbn13 = null, isbn10 = "0306406152"))

        assertTrue(result is Try.Success)
        val saved = repo.all.single()
        assertEquals("9780306406157", saved.isbn13)
        assertEquals("0306406152", saved.isbn10)
    }

    @Test
    fun `hyphenated isbn is normalized before saving`() = runTest {
        addBook(candidate(isbn13 = "978-0-306-40615-7"))

        assertEquals("9780306406157", repo.all.single().isbn13)
    }

    @Test
    fun `duplicate isbn reports the existing book and adds nothing`() = runTest {
        val existing = repo.seed(book(isbn13 = "9780306406157", title = "Existing"))

        val result = addBook(candidate())

        assertEquals(Try.Failure(AppError.Duplicate(existing.id)), result)
        assertEquals(1, repo.all.size)
    }

    @Test
    fun `manually entered books are protected from metadata refresh`() = runTest {
        addBook(candidate(source = BookSource.MANUAL))

        assertTrue(repo.all.single().userEdited)
    }

    @Test
    fun `api sourced candidates are not marked user edited`() = runTest {
        addBook(candidate(source = BookSource.GOOGLE))

        assertFalse(repo.all.single().userEdited)
    }
}
