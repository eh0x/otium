package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeCoverStore
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.model.CoverChoice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveBookTest {

    private val repo = FakeBookRepository()
    private val covers = FakeCoverStore()
    private val clock = FixedClock(now = 7_000L)
    private val saveBook = SaveBook(repo, covers, clock)

    private fun candidate(
        title: String = "Dune",
        isbn13: String? = "9780306406157",
        isbn10: String? = null,
        totalPages: Int? = 412,
        coverUrl: String? = null,
        source: BookSource = BookSource.MANUAL,
    ) = BookCandidate(
        isbn13 = isbn13,
        isbn10 = isbn10,
        title = title,
        authors = listOf("Frank Herbert"),
        totalPages = totalPages,
        tags = listOf("scifi"),
        coverUrl = coverUrl,
        source = source,
    )

    private suspend fun add(
        candidate: BookCandidate = candidate(),
        cover: CoverChoice = CoverChoice.None,
        edited: Boolean = false,
    ): Try<Long> = saveBook.add(candidate, cover, edited)

    // --- Adding ---

    @Test
    fun `saves a valid candidate with timestamps and zero progress`() = runTest {
        val result = add()

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
        val result = add(candidate(title = "   "))

        assertEquals(Try.Failure(AppError.InvalidInput("title")), result)
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `invalid isbn is rejected`() = runTest {
        val result = add(candidate(isbn13 = "9780306406158"))

        assertEquals(Try.Failure(AppError.InvalidIsbn("9780306406158")), result)
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `book without an isbn can be saved`() = runTest {
        val result = add(candidate(isbn13 = null))

        assertTrue(result is Try.Success)
        assertEquals(null, repo.all.single().isbn13)
    }

    @Test
    fun `isbn10 input is converted and both forms are stored`() = runTest {
        val result = add(candidate(isbn13 = null, isbn10 = "0306406152"))

        assertTrue(result is Try.Success)
        val saved = repo.all.single()
        assertEquals("9780306406157", saved.isbn13)
        assertEquals("0306406152", saved.isbn10)
    }

    @Test
    fun `hyphenated isbn is normalized before saving`() = runTest {
        add(candidate(isbn13 = "978-0-306-40615-7"))

        assertEquals("9780306406157", repo.all.single().isbn13)
    }

    @Test
    fun `duplicate isbn reports the existing book and adds nothing`() = runTest {
        val existing = repo.seed(book(isbn13 = "9780306406157", title = "Existing"))

        val result = add(cover = CoverChoice.Remote("https://covers/dune.jpg"))

        assertEquals(Try.Failure(AppError.Duplicate(existing.id)), result)
        assertEquals(1, repo.all.size)
        assertTrue("no cover is downloaded for a duplicate", covers.stored.isEmpty())
    }

    @Test
    fun `manually entered books are protected from metadata refresh`() = runTest {
        add(candidate(source = BookSource.MANUAL))

        assertTrue(repo.all.single().userEdited)
    }

    @Test
    fun `looked up candidates saved as found are not marked user edited`() = runTest {
        add(candidate(source = BookSource.GOOGLE), edited = false)

        assertFalse(repo.all.single().userEdited)
    }

    @Test
    fun `looked up candidates the user changed are marked user edited`() = runTest {
        add(candidate(source = BookSource.GOOGLE), edited = true)

        assertTrue(repo.all.single().userEdited)
    }

    // --- Covers when adding ---

    @Test
    fun `a remote cover is downloaded once and its path stored`() = runTest {
        add(cover = CoverChoice.Remote("https://covers/dune.jpg"))

        val saved = repo.all.single()
        assertEquals(covers.stored.single(), saved.coverPath)
        assertEquals("https://covers/dune.jpg", saved.coverSourceUrl)
    }

    @Test
    fun `a cover that cannot be downloaded does not stop the save`() = runTest {
        covers.missing += "https://covers/gone.jpg"

        val result = add(cover = CoverChoice.Remote("https://covers/gone.jpg"))

        assertTrue(result is Try.Success)
        assertNull(repo.all.single().coverPath)
    }

    @Test
    fun `a photo the user picked is stored with the book`() = runTest {
        add(cover = CoverChoice.Picked("content://photos/1"))

        assertEquals(listOf("content://photos/1"), covers.pickedUris)
        assertEquals(covers.stored.single(), repo.all.single().coverPath)
        assertNull(repo.all.single().coverSourceUrl)
    }

    @Test
    fun `a picked photo that cannot be read stops the save`() = runTest {
        covers.missing += "content://photos/gone"

        val result = add(cover = CoverChoice.Picked("content://photos/gone"))

        assertEquals(Try.Failure(AppError.InvalidInput("cover")), result)
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `a stored photo is removed again when the book cannot be saved`() = runTest {
        repo.failWith = AppError.Storage("disk full")

        add(cover = CoverChoice.Picked("content://photos/1"))

        assertTrue(covers.stored.isEmpty())
    }

    @Test
    fun `a downloaded cover is removed again when the book cannot be saved`() = runTest {
        repo.failWith = AppError.Storage("disk full")

        val result = add(cover = CoverChoice.Remote("https://covers/dune.jpg"))

        assertEquals(Try.Failure(AppError.Storage("disk full")), result)
        assertTrue(covers.stored.isEmpty())
    }

    // --- Updating ---

    private fun stored() = repo.seed(
        book(isbn13 = "9780306406157", title = "Dune", totalPages = 400, currentPage = 120)
            .copy(coverPath = "covers/old.jpg", coverSourceUrl = "https://old", addedAt = 5L),
    )

    @Test
    fun `an edit replaces the details and keeps progress and the added date`() = runTest {
        val original = stored()

        val result = saveBook.update(
            original.id,
            candidate(title = "Dune Messiah", totalPages = 336),
            CoverChoice.Unchanged,
        )

        assertEquals(Try.Success(Unit), result)
        val saved = repo.all.single()
        assertEquals("Dune Messiah", saved.title)
        assertEquals(336, saved.totalPages)
        assertEquals(120, saved.currentPage)
        assertEquals(5L, saved.addedAt)
        assertEquals(7_000L, saved.updatedAt)
        assertTrue(saved.userEdited)
        assertFalse(saved.needsLookup)
        assertEquals("covers/old.jpg", saved.coverPath)
    }

    @Test
    fun `an edit that lowers the total pages clamps the current page`() = runTest {
        val original = stored()

        saveBook.update(original.id, candidate(totalPages = 100), CoverChoice.Unchanged)

        assertEquals(100, repo.all.single().currentPage)
    }

    @Test
    fun `an edit to another book's isbn is reported as a duplicate`() = runTest {
        val original = stored()
        val other = repo.seed(book(isbn13 = "9780441013593", title = "Other"))

        val result = saveBook.update(
            original.id,
            candidate(isbn13 = "9780441013593"),
            CoverChoice.Unchanged,
        )

        assertEquals(Try.Failure(AppError.Duplicate(other.id)), result)
        assertEquals("Dune", repo.all.first { it.id == original.id }.title)
    }

    @Test
    fun `an edit with a new cover stores it and deletes the old one`() = runTest {
        val original = stored()

        saveBook.update(original.id, candidate(), CoverChoice.Picked("content://photos/2"))

        assertEquals(covers.stored.single(), repo.all.single().coverPath)
        assertEquals(listOf("covers/old.jpg"), covers.deleted)
    }

    @Test
    fun `an edit can remove the cover`() = runTest {
        val original = stored()

        saveBook.update(original.id, candidate(), CoverChoice.None)

        assertNull(repo.all.single().coverPath)
        assertNull(repo.all.single().coverSourceUrl)
        assertEquals(listOf("covers/old.jpg"), covers.deleted)
    }

    @Test
    fun `editing a book that is gone reports not found`() = runTest {
        val result = saveBook.update(99L, candidate(), CoverChoice.Unchanged)

        assertEquals(Try.Failure(AppError.NotFound), result)
    }
}
