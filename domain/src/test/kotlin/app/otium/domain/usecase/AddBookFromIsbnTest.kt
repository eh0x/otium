package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeMetadataRepository
import app.otium.domain.fakes.book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddBookFromIsbnTest {

    private val books = FakeBookRepository()
    private val metadata = FakeMetadataRepository()
    private val addFromIsbn = AddBookFromIsbn(books, metadata)

    private val dune = BookCandidate(
        isbn13 = "9780441013593",
        title = "Dune",
        authors = listOf("Frank Herbert"),
        source = BookSource.GOOGLE,
    )

    @Test
    fun `an unknown isbn is looked up and the candidate is returned without saving`() = runTest {
        metadata.isbnResult = Try.Success(dune)

        val result = addFromIsbn("9780441013593")

        assertEquals(Try.Success(dune), result)
        assertEquals(listOf("9780441013593"), metadata.lookedUp)
        assertTrue("nothing is saved before the confirm screen", books.all.isEmpty())
    }

    @Test
    fun `an isbn typed with hyphens is looked up in its normalized form`() = runTest {
        metadata.isbnResult = Try.Success(dune)

        addFromIsbn("978-0-441-01359-3")

        assertEquals(listOf("9780441013593"), metadata.lookedUp)
    }

    @Test
    fun `an isbn-10 is converted to an isbn-13 before the lookup`() = runTest {
        metadata.isbnResult = Try.Success(dune)

        addFromIsbn("0-441-01359-7")

        assertEquals(listOf("9780441013593"), metadata.lookedUp)
    }

    @Test
    fun `an invalid isbn is rejected and nobody is asked`() = runTest {
        val result = addFromIsbn("9780441013594")

        assertEquals(Try.Failure(AppError.InvalidIsbn("9780441013594")), result)
        assertTrue(metadata.lookedUp.isEmpty())
    }

    @Test
    fun `an isbn already in the library is a duplicate and costs no network call`() = runTest {
        val existing = books.seed(book(isbn13 = "9780441013593", title = "Dune"))

        val result = addFromIsbn("9780441013593")

        assertEquals(Try.Failure(AppError.Duplicate(existing.id)), result)
        assertTrue(metadata.lookedUp.isEmpty())
    }

    @Test
    fun `the duplicate check also catches an isbn-10 of a book already saved`() = runTest {
        val existing = books.seed(book(isbn13 = "9780441013593", title = "Dune"))

        val result = addFromIsbn("0441013597")

        assertEquals(Try.Failure(AppError.Duplicate(existing.id)), result)
    }

    @Test
    fun `a failed lookup is reported as it is`() = runTest {
        listOf(AppError.NotFound, AppError.Offline, AppError.RateLimited, AppError.Timeout)
            .forEach { error ->
                metadata.isbnResult = Try.Failure(error)

                assertEquals(Try.Failure(error), addFromIsbn("9780441013593"))
            }
    }
}
