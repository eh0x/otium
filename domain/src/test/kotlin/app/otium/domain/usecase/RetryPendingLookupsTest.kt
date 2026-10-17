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
    fun `a found cover replaces the one the book had, which is removed`() = runTest {
        pending(dune) { copy(coverPath = "covers/old.jpg") }
        metadata.isbnResult = found("Dune", cover = "https://covers/dune.jpg")

        retry()

        assertEquals(covers.stored.single(), repo.all.single().coverPath)
        assertEquals(listOf("covers/old.jpg"), repo.deletedCovers)
    }

    @Test
    fun `a found cover is removed again when the book cannot be saved`() = runTest {
        pending(dune)
        metadata.isbnResult = found("Dune", cover = "https://covers/dune.jpg")
        repo.failWith = AppError.Storage("disk full")

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 0, remaining = 1)), result)
        assertEquals(covers.stored, repo.deletedCovers)
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
    fun `a lookup that cannot reach the services ends the run and stays pending`() = runTest {
        pending(dune)
        pending(emma)
        metadata.isbnResults[dune] = Try.Failure(AppError.Offline)
        metadata.isbnResults[emma] = found("Emma")

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 0, remaining = 2)), result)
        assertEquals("the next book is not asked for", listOf(dune), metadata.lookedUp)
        assertTrue(repo.all.all { it.needsLookup })
    }

    @Test
    fun `a rate limited service is not asked again in the same run`() = runTest {
        pending(dune)
        pending(emma)
        metadata.isbnResults[dune] = found("Dune")
        metadata.isbnResults[emma] = Try.Failure(AppError.RateLimited)
        pending("9780306406157")

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 1, remaining = 2)), result)
        assertEquals(listOf(dune, emma), metadata.lookedUp)
    }

    @Test
    fun `any other failure keeps that book waiting and moves on`() = runTest {
        pending(dune)
        pending(emma)
        metadata.isbnResults[dune] = Try.Failure(AppError.Unexpected("bad answer"))
        metadata.isbnResults[emma] = found("Emma")

        val result = retry()

        assertEquals(Try.Success(PendingLookups(resolved = 1, remaining = 1)), result)
        assertTrue(repo.all.single { it.isbn13 == dune }.needsLookup)
        assertEquals("Emma", repo.all.single { it.isbn13 == emma }.title)
    }

    @Test
    fun `a finished book stays finished when the lookup changes its page count`() = runTest {
        pending(dune) { copy(totalPages = 300, currentPage = 300) }
        metadata.isbnResult = found("Dune", pages = 412)

        retry()

        val saved = repo.all.single()
        assertEquals(412, saved.totalPages)
        assertEquals(412, saved.currentPage)
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

    @Test
    fun `a found series fills a book that has none`() = runTest {
        pending(dune)
        metadata.isbnResult = Try.Success(
            found("Dune").value.copy(series = "Dune Chronicles", seriesIndex = 1.0),
        )

        retry()

        val saved = repo.all.single()
        assertEquals("Dune Chronicles", saved.series)
        assertEquals(1.0, saved.seriesIndex)
    }

    @Test
    fun `a series the book already has is kept`() = runTest {
        pending(dune) { copy(series = "Dune", seriesIndex = 1.0) }
        metadata.isbnResult = Try.Success(
            found("Dune").value.copy(series = "Dune Chronicles", seriesIndex = 2.0),
        )

        retry()

        val saved = repo.all.single()
        assertEquals("Dune", saved.series)
        assertEquals(1.0, saved.seriesIndex)
    }

    @Test
    fun `an invalid isbn10 from the lookup is ignored and a valid one is kept`() = runTest {
        pending(dune) { copy(isbn10 = "0441013597") }
        metadata.isbnResult = Try.Success(
            found("Dune").value.copy(isbn10 = "\"&HYPERLINK(\"http://x\")&\""),
        )

        retry()

        assertEquals("0441013597", repo.all.single().isbn10)
    }
}
