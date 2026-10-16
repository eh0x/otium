package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLibraryFiles
import app.otium.domain.fakes.FakeLibraryJson
import app.otium.domain.fakes.FakeLoanReminderScheduler
import app.otium.domain.fakes.FakeLoanRepository
import app.otium.domain.fakes.FakeNoteRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookFormat
import app.otium.domain.model.BookStatus
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.domain.transfer.ImportLoan
import app.otium.domain.transfer.ImportNote
import app.otium.domain.transfer.ImportRow
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.transfer.Shelf
import app.otium.domain.transfer.fixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportLibraryTest {

    private val repo = FakeBookRepository()
    private val files = FakeLibraryFiles()
    private val json = FakeLibraryJson()
    private var scheduled = 0
    private val noteRepo = FakeNoteRepository(repo)
    private val loanRepo = FakeLoanRepository(repo)
    private val loanReminders = FakeLoanReminderScheduler()
    private val clock = FixedClock(now = 5_000L)
    private val today = clock.todayEpochDay()
    private val import = ImportLibrary(
        books = repo,
        notes = noteRepo,
        loans = loanRepo,
        readFile = ReadLibraryFile(files, json, Dispatchers.Unconfined),
        clock = clock,
        scheduler = { scheduled++ },
        loanReminders = SyncLoanReminders(loanRepo, loanReminders, clock),
        lock = BackupLock(),
    )

    private suspend fun preview(text: String): ImportPreview {
        files.files["content://file"] = text
        return (import.preview("content://file") as Try.Success).value
    }

    private suspend fun importText(text: String): ImportResult = import.import(preview(text))

    private fun titled(title: String) = repo.all.single { it.title == title }

    @Test
    fun `a goodreads file is previewed without writing anything`() = runTest {
        repo.seed(book(isbn13 = "9780141439518", title = "Already here"))

        val preview = preview(fixture("goodreads_library_export.csv"))

        assertEquals(LibraryFormat.GOODREADS, preview.format)
        assertEquals(4, preview.rows.size)
        assertEquals(3, preview.newBooks)
        assertEquals(1, preview.alreadyInLibrary)
        assertEquals(1, preview.withoutIsbn)
        assertEquals(0, preview.unreadable)
        assertEquals(1, repo.all.size)
    }

    @Test
    fun `a storygraph file is recognised`() = runTest {
        val preview = preview(fixture("storygraph_export.csv"))

        assertEquals(LibraryFormat.STORYGRAPH, preview.format)
        assertEquals(4, preview.newBooks)
        assertEquals("a made-up id and a missing isbn are not isbns", 2, preview.withoutIsbn)
    }

    @Test
    fun `an isbn that repeats in the file counts once`() = runTest {
        val preview = preview(
            "Title,Author,Exclusive Shelf,ISBN13\nA,X,read,9780306406157\nB,Y,read,9780306406157\n",
        )

        assertEquals(1, preview.newBooks)
        assertEquals(1, preview.alreadyInLibrary)
    }

    @Test
    fun `rows without a title are left out`() = runTest {
        val preview = preview("Title,Author,Exclusive Shelf\n,Nobody,read\nKept,Somebody,read\n")

        assertEquals(1, preview.unreadable)
        assertEquals(listOf("Kept"), preview.rows.map { it.title })
    }

    @Test
    fun `a file in no known format is refused`() = runTest {
        files.files["content://file"] = "Name,Price\nTea,3\n"

        assertEquals(Try.Failure(AppError.UnknownFileFormat), import.preview("content://file"))
    }

    @Test
    fun `an empty file is refused`() = runTest {
        files.files["content://file"] = ""

        assertEquals(Try.Failure(AppError.UnknownFileFormat), import.preview("content://file"))
    }

    @Test
    fun `a file that cannot be read reports why`() = runTest {
        assertEquals(
            Try.Failure(AppError.InvalidInput("file")),
            import.preview("content://missing"),
        )
    }

    @Test
    fun `importing adds the books and plans one lookup for those with an isbn`() = runTest {
        val result = importText(fixture("goodreads_library_export.csv"))

        assertEquals(ImportResult(added = 4, skipped = 0, failed = 0, waitingForLookup = 3), result)
        assertEquals(1, scheduled)
        val pride = titled("Pride and Prejudice")
        assertEquals("9780141439518", pride.isbn13)
        assertEquals("0141439513", pride.isbn10)
        assertTrue(pride.needsLookup)
        assertFalse(pride.userEdited)
        assertEquals(1_577_145_600_000L, pride.addedAt)
        assertEquals(5_000L, pride.updatedAt)
        assertFalse("no isbn, nothing to look up", titled("Good Omens").needsLookup)
    }

    @Test
    fun `shelves become progress when the page count is known`() = runTest {
        importText(fixture("goodreads_library_export.csv"))

        assertEquals(BookStatus.READ, titled("Pride and Prejudice").status)
        assertEquals(1, titled("The Hobbit").currentPage)
        assertEquals(BookStatus.IN_PROGRESS, titled("The Hobbit").status)
        assertEquals(BookStatus.NOT_STARTED, titled("The Way of Kings").status)
    }

    @Test
    fun `without a page count a shelf is kept as a tag`() = runTest {
        importText(fixture("storygraph_export.csv"))

        assertEquals(listOf("classics", "romance", "read"), titled("Pride and Prejudice").tags)
        assertEquals(listOf("currently-reading"), titled("The Hobbit").tags)
        assertEquals(emptyList<String>(), titled("Good Omens").tags)
        assertNull("a made-up id is not kept as an isbn", titled("Unfinished Thing").isbn13)
    }

    @Test
    fun `importing the same file twice adds nothing the second time`() = runTest {
        val text = fixture("goodreads_library_export.csv")
        importText(text)

        val again = importText(text)

        assertEquals("Good Omens has no isbn, so it cannot be recognised", 1, again.added)
        assertEquals(3, again.skipped)
    }

    @Test
    fun `progress is reported after every row`() = runTest {
        val seen = mutableListOf<Pair<Int, Int>>()

        import.import(preview(fixture("goodreads_library_export.csv"))) { done, total ->
            seen += done to total
        }

        assertEquals(listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), seen)
    }

    @Test
    fun `books that cannot be stored are counted and the rest still go in`() = runTest {
        val preview = preview(fixture("goodreads_library_export.csv"))
        repo.failWith = AppError.Storage("disk full")

        val result = import.import(preview)

        assertEquals(ImportResult(added = 0, skipped = 0, failed = 4, waitingForLookup = 0), result)
        assertEquals(0, scheduled)
    }

    @Test
    fun `an otium file keeps its details and is not looked up again`() = runTest {
        json.rows = listOf(
            ImportRow(
                title = "Kept whole",
                isbn = "9780306406157",
                totalPages = 320,
                currentPage = 99,
                shelf = Shelf.READ,
                categories = listOf("History"),
                description = "Long.",
                complete = true,
                userEdited = true,
            ),
        )

        val preview = preview("{\"books\":[]}")
        val result = import.import(preview)

        assertEquals(LibraryFormat.OTIUM, preview.format)
        assertEquals(0, result.waitingForLookup)
        assertEquals(0, scheduled)
        val kept = titled("Kept whole")
        assertEquals("the exact page wins over the shelf", 99, kept.currentPage)
        assertEquals(listOf("History"), kept.categories)
        assertEquals("Long.", kept.description)
        assertTrue(kept.userEdited)
        assertFalse(kept.needsLookup)
    }

    @Test
    fun `an isbn-10 is kept only when it is valid`() = runTest {
        json.rows = listOf(
            ImportRow(title = "Valid", isbn = "9780306406157", isbn10 = "0306406152"),
            ImportRow(title = "Typo", isbn = "9780141439518", isbn10 = "0306406153"),
        )

        import.import(preview("{}"))

        assertEquals("0306406152", titled("Valid").isbn10)
        assertNull(titled("Typo").isbn10)
    }

    @Test
    fun `an exact page beyond the book is kept within it`() = runTest {
        json.rows = listOf(ImportRow(title = "Short", totalPages = 10, currentPage = 50))

        import.import(preview("{}"))

        assertEquals(10, titled("Short").currentPage)
    }

    @Test
    fun `the notes of an otium file come with the books and keep their times`() = runTest {
        json.rows = listOf(
            ImportRow(
                title = "Annotated",
                isbn = "9780306406157",
                notes = listOf(
                    ImportNote(NoteKind.QUOTE, "  A line worth keeping. ", 12, 100L, 300L),
                    ImportNote(NoteKind.NOTE, "Whole-book thought", null, 200L, 200L),
                ),
            ),
        )

        val preview = preview("{}")
        val result = import.import(preview)

        assertEquals(2, preview.notes)
        assertEquals(2, result.notesAdded)
        assertEquals(0, result.notesSkipped)
        val saved = noteRepo.of(titled("Annotated").id)
        assertEquals(
            listOf(
                Note(
                    1L,
                    titled("Annotated").id,
                    NoteKind.QUOTE,
                    "A line worth keeping.",
                    12,
                    100L,
                    300L,
                ),
                Note(
                    2L,
                    titled("Annotated").id,
                    NoteKind.NOTE,
                    "Whole-book thought",
                    null,
                    200L,
                    200L,
                ),
            ),
            saved,
        )
    }

    @Test
    fun `a note without times is dated now and never ends before it began`() = runTest {
        json.rows = listOf(
            ImportRow(
                title = "Undated",
                notes = listOf(
                    ImportNote(NoteKind.NOTE, "No times"),
                    ImportNote(
                        NoteKind.NOTE,
                        "Updated before created",
                        createdAt = 900L,
                        updatedAt = 10L,
                    ),
                    ImportNote(NoteKind.NOTE, "Bad times", createdAt = -4L, updatedAt = 0L),
                ),
            ),
        )

        import.import(preview("{}"))

        val saved = noteRepo.of(titled("Undated").id)
        assertEquals(
            listOf(5_000L, 5_000L),
            saved.first {
                it.text == "No times"
            }.let { listOf(it.createdAt, it.updatedAt) },
        )
        assertEquals(
            listOf(900L, 900L),
            saved.first {
                it.text.startsWith("Updated")
            }.let { listOf(it.createdAt, it.updatedAt) },
        )
        assertEquals(
            listOf(5_000L, 5_000L),
            saved.first {
                it.text == "Bad times"
            }.let { listOf(it.createdAt, it.updatedAt) },
        )
    }

    @Test
    fun `notes the editor would refuse are left out and counted`() = runTest {
        json.rows = listOf(
            ImportRow(
                title = "Messy",
                notes = listOf(
                    ImportNote(NoteKind.NOTE, "Fine"),
                    ImportNote(NoteKind.NOTE, "   "),
                    ImportNote(NoteKind.QUOTE, "x".repeat(Note.MAX_TEXT_LENGTH + 1)),
                    ImportNote(NoteKind.QUOTE, "Page zero", page = 0),
                ),
            ),
        )

        val preview = preview("{}")
        val result = import.import(preview)

        assertEquals("the preview counts only what will be kept", 1, preview.notes)
        assertEquals(1, result.notesAdded)
        assertEquals(3, result.notesSkipped)
        assertEquals(listOf("Fine"), noteRepo.all.map { it.text })
    }

    @Test
    fun `a text of exactly the longest length is kept`() = runTest {
        json.rows = listOf(
            ImportRow(
                title = "Long",
                notes = listOf(ImportNote(NoteKind.QUOTE, "x".repeat(Note.MAX_TEXT_LENGTH))),
            ),
        )

        val result = import.import(preview("{}"))

        assertEquals(1, result.notesAdded)
    }

    @Test
    fun `books already in the library are left alone and so are their notes`() = runTest {
        repo.seed(book(isbn13 = "9780306406157", title = "Mine"))
        json.rows = listOf(
            ImportRow(
                title = "Mine",
                isbn = "9780306406157",
                notes = listOf(ImportNote(NoteKind.NOTE, "From the file")),
            ),
            ImportRow(
                title = "New",
                isbn = "9780141439518",
                notes = listOf(ImportNote(NoteKind.NOTE, "Comes along")),
            ),
        )

        val preview = preview("{}")
        val result = import.import(preview)

        assertEquals(1, preview.notes)
        assertEquals(1, result.skipped)
        assertEquals(1, result.notesAdded)
        assertEquals(listOf("Comes along"), noteRepo.all.map { it.text })
    }

    @Test
    fun `importing the same otium file twice adds its notes once`() = runTest {
        json.rows = listOf(
            ImportRow(
                title = "Once",
                isbn = "9780306406157",
                notes = listOf(ImportNote(NoteKind.NOTE, "Only one copy")),
            ),
        )

        import.import(preview("{}"))
        val second = import.import(preview("{}"))

        assertEquals(0, second.notesAdded)
        assertEquals(1, noteRepo.all.size)
    }

    @Test
    fun `books without a note import as before`() = runTest {
        json.rows = listOf(ImportRow(title = "Plain"))

        val preview = preview("{}")
        val result = import.import(preview)

        assertEquals(0, preview.notes)
        assertEquals(0, result.notesAdded)
        assertEquals(0, result.notesSkipped)
        assertTrue(noteRepo.all.isEmpty())
    }

    // --- Format ---

    @Test
    fun `the format a file names is kept, and a file that names none leaves it unset`() = runTest {
        json.rows = listOf(
            ImportRow(title = "Heard", isbn = "9780306406157", format = BookFormat.AUDIOBOOK),
            ImportRow(title = "Unsaid", isbn = "9780141439518"),
        )

        import.import(preview("{}"))

        assertEquals(BookFormat.AUDIOBOOK, titled("Heard").format)
        assertNull(titled("Unsaid").format)
    }

    // --- Loans ---

    private fun otiumRow(title: String, isbn: String?, vararg loans: ImportLoan) =
        ImportRow(title = title, isbn = isbn, loans = loans.toList())

    @Test
    fun `the loans of an otium file come with the books and keep their days`() = runTest {
        json.rows = listOf(
            otiumRow(
                "Lent",
                "9780306406157",
                ImportLoan(LoanDirection.LENT, "  Sam ", lentOn = today - 30, remindOn = today + 5),
                ImportLoan(LoanDirection.BORROWED, "Ana", today - 90, null, today - 60),
            ),
        )

        val preview = preview("{}")
        val result = import.import(preview)

        assertEquals(2, preview.loans)
        assertEquals(2, result.loansAdded)
        assertEquals(0, result.loansSkipped)
        val id = titled("Lent").id
        assertEquals(
            listOf(
                Loan(1L, id, "Sam", LoanDirection.LENT, today - 30, today + 5),
                Loan(2L, id, "Ana", LoanDirection.BORROWED, today - 90, null, today - 60),
            ),
            loanRepo.all,
        )
    }

    @Test
    fun `loans the ledger would refuse are left out and counted`() = runTest {
        json.rows = listOf(
            otiumRow(
                "Odd",
                "9780306406157",
                ImportLoan(LoanDirection.LENT, "Fine", lentOn = today - 3),
                ImportLoan(LoanDirection.LENT, "   ", lentOn = today - 9, returnedOn = today - 8),
                ImportLoan(LoanDirection.LENT, "x".repeat(Loan.MAX_NAME_LENGTH + 1), today - 7),
                ImportLoan(LoanDirection.LENT, "Backwards", today - 5, remindOn = today - 6),
                ImportLoan(LoanDirection.LENT, "Returned early", today - 5, null, today - 6),
                ImportLoan(LoanDirection.BORROWED, "Second open", lentOn = today - 1),
            ),
        )

        val preview = preview("{}")
        val result = import.import(preview)

        assertEquals("the preview counts only what will be kept", 1, preview.loans)
        assertEquals(1, result.loansAdded)
        assertEquals(5, result.loansSkipped)
        assertEquals(listOf("Fine"), loanRepo.all.map { it.counterparty })
    }

    @Test
    fun `a book comes in with at most one open loan, and any number of returned ones`() = runTest {
        json.rows = listOf(
            otiumRow(
                "History",
                "9780306406157",
                ImportLoan(LoanDirection.LENT, "A", today - 40, null, today - 30),
                ImportLoan(LoanDirection.LENT, "B", today - 20, null, today - 10),
                ImportLoan(LoanDirection.LENT, "C", today - 2),
            ),
        )

        val result = import.import(preview("{}"))

        assertEquals(3, result.loansAdded)
        assertEquals(1, loanRepo.all.count { it.isOpen })
    }

    @Test
    fun `a loan that does not say when it began begins today`() = runTest {
        json.rows = listOf(otiumRow("Undated", null, ImportLoan(LoanDirection.LENT, "Sam")))

        import.import(preview("{}"))

        assertEquals(today, loanRepo.all.single().lentOn)
    }

    @Test
    fun `books already in the library keep their loans and take none from the file`() = runTest {
        val existing = repo.seed(book(isbn13 = "9780306406157", title = "Mine"))
        loanRepo.add(Loan(0L, existing.id, "Original", LoanDirection.LENT, today - 4))
        json.rows = listOf(
            otiumRow("Mine", "9780306406157", ImportLoan(LoanDirection.LENT, "From the file")),
            otiumRow("New", "9780141439518", ImportLoan(LoanDirection.BORROWED, "Comes along")),
        )

        val preview = preview("{}")
        val result = import.import(preview)

        assertEquals(1, preview.loans)
        assertEquals(1, result.loansAdded)
        assertEquals(listOf("Original", "Comes along"), loanRepo.all.map { it.counterparty })
    }

    @Test
    fun `importing the same otium file twice adds its loans once`() = runTest {
        json.rows = listOf(
            otiumRow("Once", "9780306406157", ImportLoan(LoanDirection.LENT, "Only one copy")),
        )

        import.import(preview("{}"))
        val second = import.import(preview("{}"))

        assertEquals(0, second.loansAdded)
        assertEquals(1, loanRepo.all.size)
    }

    @Test
    fun `reminders still ahead are planned after an import, and old ones are not`() = runTest {
        json.rows = listOf(
            otiumRow(
                "Reminded",
                "9780306406157",
                ImportLoan(LoanDirection.LENT, "Ahead", today - 3, remindOn = today + 9),
            ),
            otiumRow(
                "Stale",
                "9780141439518",
                ImportLoan(LoanDirection.LENT, "Behind", today - 30, remindOn = today - 2),
            ),
        )

        import.import(preview("{}"))

        val ahead = loanRepo.all.single { it.counterparty == "Ahead" }
        assertEquals(mapOf(ahead.id to today + 9), loanReminders.planned)
    }

    @Test
    fun `books without a loan import as before and plan nothing`() = runTest {
        json.rows = listOf(ImportRow(title = "Plain"))

        val preview = preview("{}")
        val result = import.import(preview)

        assertEquals(0, preview.loans)
        assertEquals(0, result.loansAdded)
        assertEquals(0, result.loansSkipped)
        assertTrue(loanRepo.all.isEmpty())
        assertTrue(loanReminders.planned.isEmpty())
    }
}
