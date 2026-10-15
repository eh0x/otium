package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Note
import app.otium.domain.repo.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [NoteRepository] for use case tests. Only the books in [knownBooks] exist, as a
 * foreign key would enforce in Room.
 */
class FakeNoteRepository(private val knownBooks: Set<Long> = emptySet()) : NoteRepository {
    private val notes = MutableStateFlow<Map<Long, Note>>(emptyMap())
    private var nextId = 1L

    override fun observeNotes(bookId: Long): Flow<List<Note>> = notes.map { all ->
        all.values.filter { it.bookId == bookId }
            .sortedWith(
                compareBy<Note> {
                    it.page == null
                }.thenBy { it.page }.thenBy { it.createdAt }.thenBy { it.id },
            )
    }

    override suspend fun add(note: Note): Try<Long> {
        if (note.bookId !in knownBooks) return Try.Failure(AppError.NotFound)
        val id = nextId++
        notes.value += (id to note.copy(id = id))
        return Try.Success(id)
    }

    override suspend fun update(note: Note): Try<Unit> {
        if (note.id !in notes.value) return Try.Failure(AppError.NotFound)
        notes.value += (note.id to note)
        return Try.Success(Unit)
    }

    override suspend fun delete(id: Long): Try<Unit> {
        if (id !in notes.value) return Try.Failure(AppError.NotFound)
        notes.value -= id
        return Try.Success(Unit)
    }
}
