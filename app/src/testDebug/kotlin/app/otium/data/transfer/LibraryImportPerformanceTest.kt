package app.otium.data.transfer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.otium.data.cover.CoverFiles
import app.otium.data.local.OtiumDatabase
import app.otium.data.repo.RoomBookRepository
import app.otium.data.repo.RoomLoanRepository
import app.otium.data.repo.RoomNoteRepository
import app.otium.debug.SyntheticBooks
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeLibraryFiles
import app.otium.domain.fakes.FakeLoanReminderScheduler
import app.otium.domain.fakes.FixedClock
import app.otium.domain.transfer.GoodreadsCsv
import app.otium.domain.usecase.BackupLock
import app.otium.domain.usecase.ImportLibrary
import app.otium.domain.usecase.ImportPreview
import app.otium.domain.usecase.ReadLibraryFile
import app.otium.domain.usecase.SyncLoanReminders
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the time to preview and import a 5,000-book Goodreads file. Like the search budget, this
 * runs on the host JVM with Robolectric's SQLite: a regression guard, not a device measurement.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryImportPerformanceTest {

    private lateinit var db: OtiumDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtiumDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a 5,000 book file is previewed and imported within budget`() = runTest {
        val files = FakeLibraryFiles()
        files.files["content://big"] = GoodreadsCsv.write(SyntheticBooks.generate(BOOKS))
        val loans = RoomLoanRepository(db.loanDao(), CoverFiles(File("covers")))
        val import = ImportLibrary(
            books = RoomBookRepository(db.bookDao(), CoverFiles(File("covers")), Dispatchers.IO),
            notes = RoomNoteRepository(db.noteDao()),
            loans = loans,
            readFile = ReadLibraryFile(files, JsonLibraryFormat(), Dispatchers.Unconfined),
            clock = FixedClock(),
            scheduler = {},
            loanReminders = SyncLoanReminders(loans, FakeLoanReminderScheduler(), FixedClock()),
            lock = BackupLock(),
        )

        lateinit var preview: ImportPreview
        val previewMillis = measureTimeMillis {
            preview = (import.preview("content://big") as Try.Success).value
        }
        var added = 0
        val importMillis = measureTimeMillis { added = import.import(preview).added }

        println("import of $BOOKS books: preview ${previewMillis}ms, import ${importMillis}ms")
        assertEquals(BOOKS, added)
        assertTrue("preview took ${previewMillis}ms", previewMillis < BUDGET_MILLIS)
        assertTrue("import took ${importMillis}ms", importMillis < BUDGET_MILLIS)
    }

    private companion object {
        const val BOOKS = 5_000
        const val BUDGET_MILLIS = 5_000L
    }
}
