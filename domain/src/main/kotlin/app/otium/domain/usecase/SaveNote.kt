package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.domain.port.Clock
import app.otium.domain.repo.NoteRepository

/**
 * Saves a note or quote the owner wrote. The text is trimmed and must not be blank or longer than
 * [Note.MAX_TEXT_LENGTH]; a page, when given, must be 1 or more. A page above the book's total is
 * allowed: a book's own numbering does not always match the page count in its listing.
 */
class SaveNote(private val notes: NoteRepository, private val clock: Clock) {
    /** Adds a note to book [bookId] and returns its id. */
    suspend fun add(bookId: Long, kind: NoteKind, text: String, page: Int?): Try<Long> =
        validate(text, page).flatMap { trimmed ->
            val now = clock.nowMillis()
            notes.add(Note(0L, bookId, kind, trimmed, page, createdAt = now, updatedAt = now))
        }

    /** Replaces the content of [note], keeping when it was written. */
    suspend fun update(note: Note, kind: NoteKind, text: String, page: Int?): Try<Unit> =
        validate(text, page).flatMap { trimmed ->
            notes.update(
                note.copy(kind = kind, text = trimmed, page = page, updatedAt = clock.nowMillis()),
            )
        }

    /** The trimmed text, or the field that is not acceptable. */
    private fun validate(text: String, page: Int?): Try<String> {
        val cleaned = Note.cleanText(text)
        return when {
            cleaned == null -> Try.Failure(AppError.InvalidInput("text"))
            !Note.isValidPage(page) -> Try.Failure(AppError.InvalidInput("page"))
            else -> Try.Success(cleaned)
        }
    }
}
