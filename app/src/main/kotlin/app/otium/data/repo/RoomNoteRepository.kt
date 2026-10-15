package app.otium.data.repo

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import app.otium.data.local.dao.NoteDao
import app.otium.data.local.mapper.toDomain
import app.otium.data.local.mapper.toEntity
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Note
import app.otium.domain.repo.NoteRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The owner's notes in Room. A book's notes are deleted with it by the foreign key. */
class RoomNoteRepository @Inject constructor(private val dao: NoteDao) : NoteRepository {

    override fun observeNotes(bookId: Long): Flow<List<Note>> =
        dao.observeNotes(bookId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(note: Note): Try<Long> = guard {
        try {
            Try.Success(dao.insert(note.toEntity().copy(id = 0L)))
        } catch (_: SQLiteConstraintException) {
            // The only constraint a note can break is the book it belongs to.
            Try.Failure(AppError.NotFound)
        }
    }

    override suspend fun update(note: Note): Try<Unit> = guard {
        val changed = dao.updateContent(
            note.id,
            note.kind.name,
            note.text,
            note.page,
            note.updatedAt,
        )
        if (changed > 0) Try.Success(Unit) else Try.Failure(AppError.NotFound)
    }

    override suspend fun delete(id: Long): Try<Unit> = guard {
        if (dao.delete(id) > 0) Try.Success(Unit) else Try.Failure(AppError.NotFound)
    }

    /** Converts database exceptions to [AppError.Storage]; cancellation is rethrown. */
    private inline fun <T> guard(block: () -> Try<T>): Try<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteException) {
        Try.Failure(AppError.Storage(e.message ?: "database error"))
    }
}
