package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLibraryFiles
import app.otium.domain.fakes.FakeLibraryJson
import app.otium.domain.fakes.FakeLibrarySnapshot
import app.otium.domain.fakes.FakeNoteRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
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
    private val notes = FakeNoteRepository(repo)
    private val snapshot = FakeLibrarySnapshot(repo, notes)
    private val files = FakeLibraryFiles()
    private val json = FakeLibraryJson()
    private val export = ExportLibrary(
        snapshot,
        files,
        json,
        FixedClock(now = 7L),
        Dispatchers.Unconfined,
    )

    private fun note(bookId: Long, text: String, page: Int? = null) =
        Note(0L, bookId, NoteKind.NOTE, text, page, createdAt = 1L, updatedAt = 1L)

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
    fun `the json export hands each book its notes in reading order`() = runTest {
        val first = repo.seed(book(title = "One"))
        val second = repo.seed(book(title = "Two"))
        notes.add(note(first.id, "late", page = 90))
        notes.add(note(first.id, "early", page = 3))
        notes.add(note(first.id, "whole book", page = null))

        export("content://out.json", ExportFormat.JSON)

        val written = json.written
        assertEquals(listOf("One", "Two"), written.map { it.book.title })
        assertEquals(listOf("early", "late", "whole book"), written[0].notes.map { it.text })
        assertTrue(written[1].notes.isEmpty())
        assertEquals(second.id, written[1].book.id)
    }

    @Test
    fun `the csv export has no notes`() = runTest {
        val saved = repo.seed(book(title = "One"))
        notes.add(note(saved.id, "private thought"))

        export("content://out.csv", ExportFormat.CSV)

        assertTrue("private thought" !in files.files.getValue("content://out.csv"))
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
