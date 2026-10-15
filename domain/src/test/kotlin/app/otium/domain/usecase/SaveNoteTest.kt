package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeNoteRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveNoteTest {

    private val repo = FakeNoteRepository(knownBooks = setOf(7L))
    private val clock = FixedClock(now = 5_000L)
    private val saveNote = SaveNote(repo, clock)

    private suspend fun notes(bookId: Long = 7L) = repo.observeNotes(bookId).first()

    @Test
    fun `adds a note with the time it was written`() = runTest {
        val result = saveNote.add(7L, NoteKind.QUOTE, "A passage", page = 42)

        assertTrue(result is Try.Success)
        assertEquals(
            Note(1L, 7L, NoteKind.QUOTE, "A passage", 42, createdAt = 5_000L, updatedAt = 5_000L),
            notes().single(),
        )
    }

    @Test
    fun `trims the text`() = runTest {
        saveNote.add(7L, NoteKind.NOTE, "  spaced out \n", page = null)

        assertEquals("spaced out", notes().single().text)
    }

    @Test
    fun `blank text is rejected and nothing is stored`() = runTest {
        val result = saveNote.add(7L, NoteKind.NOTE, " \n\t", page = null)

        assertEquals(Try.Failure(AppError.InvalidInput("text")), result)
        assertTrue(notes().isEmpty())
    }

    @Test
    fun `text over the limit is rejected`() = runTest {
        val result = saveNote.add(7L, NoteKind.QUOTE, "x".repeat(Note.MAX_TEXT_LENGTH + 1), 1)

        assertEquals(Try.Failure(AppError.InvalidInput("text")), result)
    }

    @Test
    fun `text at the limit is kept`() = runTest {
        val result = saveNote.add(7L, NoteKind.QUOTE, "x".repeat(Note.MAX_TEXT_LENGTH), 1)

        assertTrue(result is Try.Success)
    }

    @Test
    fun `a page below one is rejected`() = runTest {
        assertEquals(
            Try.Failure(AppError.InvalidInput("page")),
            saveNote.add(7L, NoteKind.NOTE, "text", page = 0),
        )
        assertEquals(
            Try.Failure(AppError.InvalidInput("page")),
            saveNote.add(7L, NoteKind.NOTE, "text", page = -3),
        )
    }

    @Test
    fun `a note on a missing book fails with not found`() = runTest {
        val result = saveNote.add(99L, NoteKind.NOTE, "text", page = null)

        assertEquals(Try.Failure(AppError.NotFound), result)
    }

    @Test
    fun `update changes the content, keeps the creation time and stamps the update`() = runTest {
        saveNote.add(7L, NoteKind.NOTE, "first", page = 1)
        val later = SaveNote(repo, FixedClock(now = 9_000L))

        val result = later.update(notes().single(), NoteKind.QUOTE, " second ", page = null)

        assertTrue(result is Try.Success)
        assertEquals(
            Note(1L, 7L, NoteKind.QUOTE, "second", null, createdAt = 5_000L, updatedAt = 9_000L),
            notes().single(),
        )
    }

    @Test
    fun `update with blank text is rejected and the note is unchanged`() = runTest {
        saveNote.add(7L, NoteKind.NOTE, "first", page = 1)
        val stored = notes().single()

        val result = saveNote.update(stored, NoteKind.NOTE, "  ", page = 1)

        assertEquals(Try.Failure(AppError.InvalidInput("text")), result)
        assertEquals(stored, notes().single())
    }

    @Test
    fun `update of a deleted note fails with not found`() = runTest {
        saveNote.add(7L, NoteKind.NOTE, "first", page = 1)
        val stored = notes().single()
        repo.delete(stored.id)

        assertEquals(
            Try.Failure(AppError.NotFound),
            saveNote.update(stored, NoteKind.NOTE, "again", page = 1),
        )
    }
}
