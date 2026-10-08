package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeCoverStore
import app.otium.domain.fakes.FakeMetadataRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.Book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPendingLookupsTest {

    private val repo = FakeBookRepository()
    private val metadata = FakeMetadataRepository()
    private val covers = FakeCoverStore()
    private val retry = RetryPendingLookups(repo, metadata, covers, FixedClock(now = 8_000L))

    private val dune = "9780441013593"
    private val emma = "9780141439587"

    private fun pending(isbn13: String, configure: Book.() -> Book = { this }): Book = repo.seed(
        book(isbn13 = isbn13, title = isbn13, totalPages = 0)
            .copy(needsLookup = true, addedAt = 3L)
            .configure(),
    )

    private fun found(title: String, pages: Int? = 412, cover: String? = null) = Try.Success(
        BookCandidate(
            title = title,
            authors = listOf("Frank Herbert"),
            totalPages = pages,
            categories = listOf("Fiction"),
            coverUrl = cover,
            source = BookSource.GOOGLE,
        ),
    )

    @Test
    fun `a found book gets its details and stops waiting`() = runTest {
        val placeholder = pending(dune) { copy(currentPage = 50, tags = listOf("to-read")) }
        metadata.isbnResult = found("Dune", cover = "https://covers/dune.jpg")

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 1, remaining = 0)), result)
        val saved = repo.all.single()
        assertEquals(placeholder.id, saved.id)
        assertEquals("Dune", saved.title)
        assertEquals(listOf("Frank Herbert"), saved.authors)
        assertEquals(412, saved.totalPages)
        assertEquals(50, saved.currentPage)
        assertEquals(listOf("to-read"), saved.tags)
        assertEquals(3L, saved.addedAt)
        assertEquals(8_000L, saved.updatedAt)
        assertEquals(BookSource.GOOGLE, saved.source)
        assertEquals(covers.stored.single(), saved.coverPath)
        assertFalse(saved.needsLookup)
        assertFalse(saved.userEdited)
    }

    @Test
    fun `a book nobody knows stops waiting and keeps what it has`() = runTest {
        pending(dune)
        metadata.isbnResult = Try.Failure(AppError.NotFound)

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 1, remaining = 0)), result)
        val saved = repo.all.single()
        assertEquals(dune, saved.title)
        assertFalse(saved.needsLookup)
    }

    @Test
    fun `a lookup that cannot reach the services stays pending for a later try`() = runTest {
        pending(dune)
        pending(emma)
        metadata.isbnResults[dune] = Try.Failure(AppError.Offline)
        metadata.isbnResults[emma] = found("Emma")

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 1, remaining = 1)), result)
        assertTrue(repo.all.single { it.isbn13 == dune }.needsLookup)
        assertEquals("Emma", repo.all.single { it.isbn13 == emma }.title)
    }

    @Test
    fun `a book the user edited meanwhile is not overwritten`() = runTest {
        pending(dune) { copy(title = "My Dune", userEdited = true) }
        metadata.isbnResult = found("Dune")

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 1, remaining = 0)), result)
        assertTrue("no lookup for a book the user curated", metadata.lookedUp.isEmpty())
        val saved = repo.all.single()
        assertEquals("My Dune", saved.title)
        assertFalse(saved.needsLookup)
    }

    @Test
    fun `an unknown page count from the lookup keeps the one already set`() = runTest {
        pending(dune) { copy(totalPages = 300) }
        metadata.isbnResult = found("Dune", pages = null)

        retry()

        assertEquals(300, repo.all.single().totalPages)
    }

    @Test
    fun `books that are not waiting are left alone`() = runTest {
        repo.seed(book(isbn13 = dune, title = "Dune"))

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 0, remaining = 0)), result)
        assertTrue(metadata.lookedUp.isEmpty())
    }
}
