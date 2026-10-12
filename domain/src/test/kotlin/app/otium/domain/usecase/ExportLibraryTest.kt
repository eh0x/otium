package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLibraryFiles
import app.otium.domain.fakes.FakeLibraryJson
import app.otium.domain.fakes.FakeLibrarySnapshot
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.transfer.CsvTable
import app.otium.domain.transfer.ExportFormat
import app.otium.domain.transfer.GoodreadsCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportLibraryTest {

    private val repo = FakeBookRepository()
    private val snapshot = FakeLibrarySnapshot(repo)
    private val files = FakeLibraryFiles()
    private val export = ExportLibrary(
        snapshot,
        files,
        FakeLibraryJson(),
        FixedClock(now = 7L),
        Dispatchers.Unconfined,
    )

    @Test
    fun `the csv export holds every book in goodreads columns`() = runTest {
        repo.seed(book(title = "One"))
        repo.seed(book(title = "Two"))

        val result = export("content://out.csv", ExportFormat.CSV)

        assertEquals(Try.Success(2), result)
        val table = CsvTable.of(files.files.getValue("content://out.csv"))!!
        assertTrue(GoodreadsCsv.matches(table))
        assertEquals(listOf("One", "Two"), table.records.map { it["Title"] })
    }

    @Test
    fun `the json export is written by the json library with the time`() = runTest {
        repo.seed(book(title = "One"))

        export("content://out.json", ExportFormat.JSON)

        assertEquals("{\"books\":1,\"exportedAt\":7}", files.files["content://out.json"])
    }

    @Test
    fun `an empty library exports an empty file`() = runTest {
        assertEquals(Try.Success(0), export("content://out.csv", ExportFormat.CSV))
        val table = CsvTable.of(files.files.getValue("content://out.csv"))!!
        assertTrue("the header alone", GoodreadsCsv.matches(table) && table.records.isEmpty())
    }

    @Test
    fun `a library that cannot be read writes nothing`() = runTest {
        snapshot.failWith = AppError.Storage("locked")

        assertEquals(
            Try.Failure(AppError.Storage("locked")),
            export("content://x", ExportFormat.CSV),
        )
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun `a file that cannot be written reports why`() = runTest {
        files.failWith = AppError.Storage("gone")

        assertEquals(
            Try.Failure(AppError.Storage("gone")),
            export("content://x", ExportFormat.JSON),
        )
    }
}
