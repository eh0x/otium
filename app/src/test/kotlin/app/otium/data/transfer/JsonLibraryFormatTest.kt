package app.otium.data.transfer

import app.otium.data.local.testBook
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonLibraryFormatTest {

    private val format = JsonLibraryFormat()

    private fun rows(text: String) = (format.read(text) as Try.Success).value

    @Test
    fun `what is written reads back with every field`() {
        val book = testBook(
            title = "Dune",
            subtitle = "Book one",
            authors = listOf("Frank Herbert"),
            isbn13 = "9780441013593",
            categories = listOf("Fiction"),
            tags = listOf("signed", "loaned"),
            totalPages = 412,
            currentPage = 40,
            addedAt = 11L,
        ).copy(isbn10 = "0441013597", source = BookSource.GOOGLE, userEdited = true)

        val row = rows(format.write(listOf(book), exportedAt = 99L)).single()

        assertEquals("Dune", row.title)
        assertEquals("Book one", row.subtitle)
        assertEquals(listOf("Frank Herbert"), row.authors)
        assertEquals("9780441013593", row.isbn)
        assertEquals("0441013597", row.isbn10)
        assertEquals("Publisher", row.publisher)
        assertEquals(1999, row.publishedYear)
        assertEquals("en", row.language)
        assertEquals("Description", row.description)
        assertEquals(412, row.totalPages)
        assertEquals(40, row.currentPage)
        assertEquals(listOf("Fiction"), row.categories)
        assertEquals(listOf("signed", "loaned"), row.tags)
        assertEquals("https://example.org/cover.jpg", row.coverSourceUrl)
        assertEquals(BookSource.GOOGLE, row.source)
        assertEquals(11L, row.addedAt)
        assertTrue(row.userEdited)
        assertTrue("a book with its details needs no lookup", row.complete)
    }

    @Test
    fun `a book still waiting for its lookup keeps waiting after an import`() {
        val waiting = testBook(isbn13 = "9780441013593").copy(needsLookup = true)

        assertFalse(rows(format.write(listOf(waiting), exportedAt = 1L)).single().complete)
    }

    @Test
    fun `the file names its format and version`() {
        val text = format.write(emptyList(), exportedAt = 5L)

        assertTrue(text.contains("\"format\": \"otium-library\""))
        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"exportedAt\": 5"))
    }

    @Test
    fun `missing optional fields and unknown keys are tolerated`() {
        val row = rows(
            """{"format":"otium-library","version":1,"books":[{"title":"Bare","later":true}]}""",
        ).single()

        assertEquals("Bare", row.title)
        assertEquals(0, row.totalPages)
        assertEquals(BookSource.MANUAL, row.source)
    }

    @Test
    fun `json that is not an otium library is refused`() {
        assertEquals(
            Try.Failure(AppError.UnknownFileFormat),
            format.read("""{"format":"something-else","version":1,"books":[]}"""),
        )
        assertEquals(Try.Failure(AppError.UnknownFileFormat), format.read("""{"books":"#"}"""))
        assertEquals(Try.Failure(AppError.UnknownFileFormat), format.read("{ not json"))
    }

    @Test
    fun `a file from a newer version is refused rather than read in part`() {
        assertEquals(
            Try.Failure(AppError.IncompatibleBackup(backupSchema = 2, appSchema = 1)),
            format.read("""{"format":"otium-library","version":2,"books":[]}"""),
        )
    }
}
