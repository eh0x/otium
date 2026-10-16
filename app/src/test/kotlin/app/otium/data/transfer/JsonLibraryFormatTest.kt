package app.otium.data.transfer

import app.otium.data.local.testBook
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BookFormat
import app.otium.domain.model.BookSource
import app.otium.domain.model.LibraryEntry
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.domain.transfer.ImportLoan
import app.otium.domain.transfer.ImportNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertTrue(text.contains("\"version\": 3"))
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
            Try.Failure(AppError.IncompatibleBackup(backupSchema = 4, appSchema = 3)),
            format.read("""{"format":"otium-library","version":4,"books":[]}"""),
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
    fun `loans are written with their book, oldest first, and read back`() {
        val loans = listOf(
            Loan(1L, 7L, "Ana", LoanDirection.BORROWED, 19_000L, null, 19_010L),
            Loan(2L, 7L, "Sam", LoanDirection.LENT, 20_000L, 20_030L, null),
        )

        val text = format.write(listOf(LibraryEntry(testBook(), loans = loans)), 1L)

        assertEquals(
            listOf(
                ImportLoan(LoanDirection.BORROWED, "Ana", 19_000L, null, 19_010L),
                ImportLoan(LoanDirection.LENT, "Sam", 20_000L, 20_030L, null),
            ),
            rows(text).single().loans,
        )
    }

    @Test
    fun `a format is written with its book and read back`() {
        val book = testBook(title = "Heard").copy(format = BookFormat.AUDIOBOOK)

        val text = format.write(listOf(LibraryEntry(book)), exportedAt = 1L)

        assertEquals(BookFormat.AUDIOBOOK, rows(text).single().format)
    }

    @Test
    fun `a book without a format writes none and an older file reads as unset`() {
        val text = format.write(listOf(LibraryEntry(testBook())), exportedAt = 1L)

        assertEquals("only the file's own format key", 1, Regex("\"format\"").findAll(text).count())
        assertNull(rows(text).single().format)
        assertNull(
            rows("""{"format":"otium-library","version":3,"books":[{"title":"Old"}]}""")
                .single().format,
        )
    }

    @Test
    fun `a format this app does not know reads as unset`() {
        val row = rows(
            """{"format":"otium-library","version":3,"books":[{"title":"B","format":"VINYL"}]}""",
        ).single()

        assertNull(row.format)
    }

    @Test
    fun `a book without loans writes none and reads back none`() {
        val text = format.write(listOf(LibraryEntry(testBook())), exportedAt = 1L)

        assertFalse(text.contains("loans"))
        assertTrue(rows(text).single().loans.isEmpty())
    }

    @Test
    fun `a version 2 file still reads, with its notes and without loans`() {
        val row = rows(
            """{"format":"otium-library","version":2,"books":[{"title":"Old",
                "notes":[{"text":"Kept"}]}]}""",
        ).single()

        assertEquals("Old", row.title)
        assertEquals(1, row.notes.size)
        assertTrue(row.loans.isEmpty())
    }

    @Test
    fun `a loan with missing or unknown parts is read as far as it goes`() {
        val row = rows(
            """{"format":"otium-library","version":3,"books":[{"title":"B","loans":[
                {"counterparty":"No direction or days"},
                {"direction":"GIFTED","counterparty":"Unknown direction","lentOn":5,"extra":1},
                {"direction":"BORROWED"}
            ]}]}""",
        ).single()

        assertEquals(
            listOf(
                ImportLoan(LoanDirection.LENT, "No direction or days"),
                ImportLoan(LoanDirection.LENT, "Unknown direction", lentOn = 5L),
                ImportLoan(LoanDirection.BORROWED, ""),
            ),
            row.loans,
        )
    }

    @Test
    fun `a name with quotes, newlines and unicode survives the round trip`() {
        val name = "Zoë “Z” O'Brien\n(next door) \\ 山田"
        val loans = listOf(Loan(1L, 1L, name, LoanDirection.LENT, 1L))

        val row = rows(format.write(listOf(LibraryEntry(testBook(), loans = loans)), 1L)).single()

        assertEquals(name, row.loans.single().counterparty)
    }

    @Test
    fun `text with quotes, newlines and unicode survives the round trip`() {
        val text = "“Quoted”\nline two — ünïcode \"and\" \\ slash"
        val notes = listOf(Note(1L, 1L, NoteKind.QUOTE, text, 1, 1L, 1L))

        val row = rows(format.write(listOf(LibraryEntry(testBook(), notes)), 1L)).single()

        assertEquals(text, row.notes.single().text)
    }
}
