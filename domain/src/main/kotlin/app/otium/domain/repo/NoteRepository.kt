package app.otium.domain.repo

import app.otium.domain.error.Try
import app.otium.domain.model.Note
import kotlinx.coroutines.flow.Flow

/** The owner's notes and quotes. Room is the source of truth; the implementation is in :app. */
interface NoteRepository {
    /** A book's notes in reading order: by page, then oldest first; notes without a page last. */
    fun observeNotes(bookId: Long): Flow<List<Note>>

    /** Fails with AppError.NotFound when the book does not exist. Returns the new id. */
    suspend fun add(note: Note): Try<Long>

    /** Replaces the note's kind, text, page and update time. Fails with AppError.NotFound. */
    suspend fun update(note: Note): Try<Unit>

    suspend fun delete(id: Long): Try<Unit>
}
