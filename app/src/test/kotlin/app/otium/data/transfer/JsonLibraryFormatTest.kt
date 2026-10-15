package app.otium.data.transfer

import app.otium.data.local.testBook
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BookSource
import app.otium.domain.model.LibraryEntry
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.domain.transfer.ImportNote
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
            series = "Dune Chronicles",
            seriesIndex = 1.5,
        ).copy(isbn10 = "0441013597", source = BookSource.GOOGLE, userEdited = true)

        val row = rows(format.write(listOf(LibraryEntry(book)), exportedAt = 99L)).single()

        assertEquals("Dune", row.title)
        assertEquals("Book one", row.subtitle)
        assertEquals("Dune Chronicles", row.series)
        assertEquals(1.5, row.seriesIndex)
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

        val text = format.write(listOf(LibraryEntry(waiting)), exportedAt = 1L)

        assertFalse(rows(text).single().complete)
    }

    @Test
    fun `the file names its format and version`() {
        val text = format.write(emptyList(), exportedAt = 5L)

        assertTrue(text.contains("\"format\": \"otium-library\""))
        assertTrue(text.contains("\"version\": 2"))
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
            Try.Failure(AppError.IncompatibleBackup(backupSchema = 3, appSchema = 2)),
            format.read("""{"format":"otium-library","version":3,"books":[]}"""),
        )
    }

    @Test
    fun `notes are written with their book in reading order and read back`() {
        val notes = listOf(
            Note(1L, 7L, NoteKind.QUOTE, "Fear is the mind-killer.", 12, 100L, 300L),
            Note(2L, 7L, NoteKind.NOTE, "Reread this.", null, 200L, 200L),
        )

        val row = rows(format.write(listOf(LibraryEntry(testBook(), notes)), 1L)).single()

        assertEquals(
            listOf(
                ImportNote(NoteKind.QUOTE, "Fear is the mind-killer.", 12, 100L, 300L),
                ImportNote(NoteKind.NOTE, "Reread this.", null, 200L, 200L),
            ),
            row.notes,
        )
    }

    @Test
    fun `a book without notes writes none and reads back none`() {
        val text = format.write(listOf(LibraryEntry(testBook())), exportedAt = 1L)

        assertFalse(text.contains("notes"))
        assertTrue(rows(text).single().notes.isEmpty())
    }

    @Test
    fun `a version 1 file still reads, without notes`() {
        val row = rows(
            """{"format":"otium-library","version":1,"books":[{"title":"Old","totalPages":10}]}""",
        ).single()

        assertEquals("Old", row.title)
        assertTrue(row.notes.isEmpty())
    }

    @Test
    fun `a note with missing or unknown parts is read as far as it goes`() {
        val row = rows(
            """{"format":"otium-library","version":2,"books":[{"title":"B","notes":[
                {"text":"No kind or times"},
                {"kind":"HIGHLIGHT","text":"Unknown kind","page":4,"extra":1},
                {"kind":"QUOTE"}
            ]}]}""",
        ).single()

        assertEquals(
            listOf(
                ImportNote(NoteKind.NOTE, "No kind or times"),
                ImportNote(NoteKind.NOTE, "Unknown kind", page = 4),
                ImportNote(NoteKind.QUOTE, ""),
            ),
            row.notes,
        )
    }

    @Test
    fun `text with quotes, newlines and unicode survives the round trip`() {
        val text = "“Quoted”\nline two — ünïcode \"and\" \\ slash"
        val notes = listOf(Note(1L, 1L, NoteKind.QUOTE, text, 1, 1L, 1L))

        val row = rows(format.write(listOf(LibraryEntry(testBook(), notes)), 1L)).single()

        assertEquals(text, row.notes.single().text)
    }
}
