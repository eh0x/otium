package app.otium.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.otium.data.cover.CoverFiles
import app.otium.data.repo.RoomBookRepository
import app.otium.data.repo.RoomNoteRepository
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {

    private lateinit var db: OtiumDatabase
    private lateinit var books: RoomBookRepository
    private lateinit var notes: RoomNoteRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtiumDatabase::class.java,
        ).allowMainThreadQueries().build()
        books = RoomBookRepository(db.bookDao(), CoverFiles(File("covers")), Dispatchers.Unconfined)
        notes = RoomNoteRepository(db.noteDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addBook(title: String = "Title"): Long =
        (books.add(testBook(title = title)) as Try.Success).value

    private fun note(
        bookId: Long,
        text: String = "text",
        page: Int? = null,
        kind: NoteKind = NoteKind.NOTE,
        createdAt: Long = 1L,
    ) = Note(0L, bookId, kind, text, page, createdAt, createdAt)

    private suspend fun add(note: Note): Long = (notes.add(note) as Try.Success).value

    private suspend fun texts(bookId: Long) = notes.observeNotes(bookId).first().map { it.text }

    @Test
    fun `a note is stored with everything it holds`() = runTest {
        val bookId = addBook()
        val id = add(note(bookId, "In a hole", page = 1, kind = NoteKind.QUOTE, createdAt = 5L))

        assertEquals(
            Note(id, bookId, NoteKind.QUOTE, "In a hole", 1, createdAt = 5L, updatedAt = 5L),
            notes.observeNotes(bookId).first().single(),
        )
    }

    @Test
    fun `notes come in reading order, unpaged last, oldest first on the same page`() = runTest {
        val bookId = addBook()
        add(note(bookId, "unpaged", page = null, createdAt = 1L))
        add(note(bookId, "page 30", page = 30, createdAt = 2L))
        add(note(bookId, "page 5 later", page = 5, createdAt = 4L))
        add(note(bookId, "page 5 first", page = 5, createdAt = 3L))

        assertEquals(
            listOf("page 5 first", "page 5 later", "page 30", "unpaged"),
            texts(bookId),
        )
    }

    @Test
    fun `a book sees only its own notes`() = runTest {
        val first = addBook("First")
        val second = addBook("Second")
        add(note(first, "mine"))
        add(note(second, "theirs"))

        assertEquals(listOf("mine"), texts(first))
        assertEquals(listOf("theirs"), texts(second))
    }

    @Test
    fun `a note on a book that does not exist fails with not found`() = runTest {
        val result = notes.add(note(bookId = 404L))

        assertEquals(Try.Failure(AppError.NotFound), result)
    }

    @Test
    fun `update replaces kind, text, page and update time but not who or when it was written`() =
        runTest {
            val bookId = addBook()
            val id = add(note(bookId, "first", page = 1, createdAt = 5L))

            val result = notes.update(
                Note(id, bookId, NoteKind.QUOTE, "second", null, createdAt = 99L, updatedAt = 8L),
            )

            assertEquals(Try.Success(Unit), result)
            assertEquals(
                Note(id, bookId, NoteKind.QUOTE, "second", null, createdAt = 5L, updatedAt = 8L),
                notes.observeNotes(bookId).first().single(),
            )
        }

    @Test
    fun `update of a note that does not exist fails with not found`() = runTest {
        val bookId = addBook()

        assertEquals(
            Try.Failure(AppError.NotFound),
            notes.update(Note(404L, bookId, NoteKind.NOTE, "x", null, 1L, 1L)),
        )
    }

    @Test
    fun `delete removes the note, and a second delete fails with not found`() = runTest {
        val bookId = addBook()
        val id = add(note(bookId))

        assertEquals(Try.Success(Unit), notes.delete(id))
        assertTrue(notes.observeNotes(bookId).first().isEmpty())
        assertEquals(Try.Failure(AppError.NotFound), notes.delete(id))
    }

    @Test
    fun `deleting a book deletes its notes and leaves other books' notes`() = runTest {
        val gone = addBook("Gone")
        val kept = addBook("Kept")
        add(note(gone, "goes"))
        add(note(kept, "stays"))

        books.delete(gone)

        assertTrue(notes.observeNotes(gone).first().isEmpty())
        assertEquals(listOf("stays"), texts(kept))
    }
}
