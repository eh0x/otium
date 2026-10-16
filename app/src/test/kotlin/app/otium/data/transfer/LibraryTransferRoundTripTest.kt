package app.otium.data.transfer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.otium.data.cover.CoverFiles
import app.otium.data.local.OtiumDatabase
import app.otium.data.local.testBook
import app.otium.data.repo.RoomBookRepository
import app.otium.data.repo.RoomLoanRepository
import app.otium.data.repo.RoomNoteRepository
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeLibraryFiles
import app.otium.domain.fakes.FakeLoanReminderScheduler
import app.otium.domain.fakes.FixedClock
import app.otium.domain.model.Book
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryEntry
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.domain.transfer.ExportFormat
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.usecase.BackupLock
import app.otium.domain.usecase.ExportLibrary
import app.otium.domain.usecase.ImportLibrary
import app.otium.domain.usecase.ReadLibraryFile
import app.otium.domain.usecase.SyncLoanReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A library exported from one database and imported into an empty one, through Room. */
@RunWith(RobolectricTestRunner::class)
class LibraryTransferRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var source: OtiumDatabase
    private lateinit var target: OtiumDatabase
    private val files = FakeLibraryFiles()
    private val loanReminders = FakeLoanReminderScheduler()
    private val covers by lazy { CoverFiles(temp.newFolder("covers")) }

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        OtiumDatabase::class.java,
    ).allowMainThreadQueries().build()

    private fun OtiumDatabase.repository() =
        RoomBookRepository(bookDao(), covers, Dispatchers.Unconfined)

    @Before
    fun setUp() {
        source = database()
        target = database()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private val books = listOf(
        testBook(
            title = "Dune",
            authors = listOf("Frank Herbert", "Brian Herbert"),
            isbn13 = "9780441013593",
            categories = listOf("Fiction", "Classics"),
            tags = listOf("signed", "loaned"),
            totalPages = 412,
            currentPage = 412,
            addedAt = DAY_ONE,
        ),
        testBook(title = "Notes, \"quoted\"", totalPages = 0, addedAt = DAY_ONE + DAY),
        testBook(
            title = "Halfway",
            isbn13 = "9780306406157",
            currentPage = 150,
            addedAt =
                DAY_ONE + 2 * DAY,
        ),
    )

    private val quote = Note(0L, 0L, NoteKind.QUOTE, "“Fear”\nis the mind-killer.", 12, 100L, 300L)
    private val thought = Note(0L, 0L, NoteKind.NOTE, "Reread this.", null, 200L, 200L)
    private val earlier = Loan(0L, 0L, "Ana", LoanDirection.BORROWED, 19_000L, null, 19_010L)
    private val lentOut = Loan(0L, 0L, "Sam", LoanDirection.LENT, 20_000L, 20_030L)
    private val borrowed = Loan(0L, 0L, "Mira O'Neil", LoanDirection.BORROWED, 20_100L)

    private suspend fun exportThenImport(format: ExportFormat): List<LibraryEntry> {
        val from = source.repository()
        val ids = books.map { (from.add(it) as Try.Success).value }
        val fromNotes = RoomNoteRepository(source.noteDao())
        fromNotes.add(thought.copy(bookId = ids[0]))
        fromNotes.add(quote.copy(bookId = ids[0]))
        fromNotes.add(quote.copy(bookId = ids[2], text = "Halfway there", page = 150))
        val fromLoans = RoomLoanRepository(source.loanDao(), covers)
        fromLoans.add(earlier.copy(bookId = ids[0]))
        fromLoans.add(lentOut.copy(bookId = ids[0]))
        fromLoans.add(borrowed.copy(bookId = ids[2]))
        val export = ExportLibrary(
            RoomLibrarySnapshot(source, covers),
            files,
            JsonLibraryFormat(),
            FixedClock(now = 1L),
            Dispatchers.Unconfined,
        )
        assertEquals(Try.Success(3), export("content://library", format))

        val to = target.repository()
        val toLoans = RoomLoanRepository(target.loanDao(), covers)
        val import = ImportLibrary(
            books = to,
            notes = RoomNoteRepository(target.noteDao()),
            loans = toLoans,
            readFile = ReadLibraryFile(files, JsonLibraryFormat(), Dispatchers.Unconfined),
            clock = FixedClock(now = 9L),
            scheduler = {},
            loanReminders = SyncLoanReminders(toLoans, loanReminders, FixedClock(now = 9L)),
            lock = BackupLock(),
        )
        val preview = (import.preview("content://library") as Try.Success).value
        assertEquals(
            if (format == ExportFormat.JSON) LibraryFormat.OTIUM else LibraryFormat.GOODREADS,
            preview.format,
        )
        assertEquals(3, import.import(preview).added)
        return (RoomLibrarySnapshot(target, covers).entries() as Try.Success).value
    }

    @Test
    fun `the json export brings every book back whole`() = runTest {
        val back = exportThenImport(ExportFormat.JSON).map { it.book }

        val comparable = { book: Book -> book.copy(id = 0L, updatedAt = 0L) }
        assertEquals(books.map(comparable), back.map(comparable))
    }

    @Test
    fun `the json export brings every note back with its book, page and times`() = runTest {
        val back = exportThenImport(ExportFormat.JSON)

        val comparable = { note: Note -> note.copy(id = 0L, bookId = 0L) }
        assertEquals(
            listOf(
                listOf(quote, thought).map(comparable),
                emptyList(),
                listOf(quote.copy(text = "Halfway there", page = 150)).map(comparable),
            ),
            back.map { entry -> entry.notes.map(comparable) },
        )
        assertTrue(
            "each note belongs to the new copy of its book",
            back.all { entry -> entry.notes.all { it.bookId == entry.book.id } },
        )
    }

    @Test
    fun `the csv export leaves the notes behind`() = runTest {
        val back = exportThenImport(ExportFormat.CSV)

        assertTrue(back.all { it.notes.isEmpty() })
    }

    @Test
    fun `the json export brings every loan back with its book, history and days`() = runTest {
        val back = exportThenImport(ExportFormat.JSON)

        val comparable = { loan: Loan -> loan.copy(id = 0L, bookId = 0L) }
        assertEquals(
            listOf(
                listOf(earlier, lentOut).map(comparable),
                emptyList(),
                listOf(borrowed).map(comparable),
            ),
            back.map { entry -> entry.loans.map(comparable) },
        )
        assertTrue(
            "each loan belongs to the new copy of its book",
            back.all { entry -> entry.loans.all { it.bookId == entry.book.id } },
        )
    }

    @Test
    fun `an imported loan with a reminder still ahead is reminded on its day`() = runTest {
        val back = exportThenImport(ExportFormat.JSON)

        val sams = back[0].loans.single { it.counterparty == "Sam" }
        assertEquals(mapOf(sams.id to 20_030L), loanReminders.planned)
    }

    @Test
    fun `the csv export leaves the loans behind`() = runTest {
        val back = exportThenImport(ExportFormat.CSV)

        assertTrue(back.all { it.loans.isEmpty() })
    }

    @Test
    fun `the csv export brings titles, authors, isbns, progress and labels back`() = runTest {
        val back = exportThenImport(ExportFormat.CSV).map { it.book }

        assertEquals(books.map { it.title }, back.map { it.title })
        assertEquals(books.map { it.authors }, back.map { it.authors })
        assertEquals(books.map { it.isbn13 }, back.map { it.isbn13 })
        assertEquals(books.map { it.addedAt }, back.map { it.addedAt })
        assertEquals(
            listOf(BookStatus.READ, BookStatus.NOT_STARTED, BookStatus.IN_PROGRESS),
            back.map { it.status },
        )
        assertEquals(listOf("Fiction", "Classics", "signed", "loaned"), back[0].tags)
        assertTrue("books with an isbn are looked up again", back[0].needsLookup)
    }

    @Test
    fun `the snapshot keeps labels in the order they were given`() = runTest {
        books.forEach { source.repository().add(it) }

        val read = (RoomLibrarySnapshot(source, covers).entries() as Try.Success).value
            .map { it.book }

        assertEquals(listOf("Frank Herbert", "Brian Herbert"), read[0].authors)
        assertEquals(listOf("Fiction", "Classics"), read[0].categories)
        assertEquals(listOf("signed", "loaned"), read[0].tags)
    }
}

/** 2019-12-24 UTC. The CSV keeps the day a book was added, not the time. */
private const val DAY_ONE = 1_577_145_600_000L
private const val DAY = 86_400_000L
