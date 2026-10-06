package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.BookCandidate
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository

/**
 * Turns an ISBN, typed or scanned, into a candidate for the confirm screen. The ISBN is checked
 * and normalized to ISBN-13 first; a book already in the library is reported as a duplicate
 * before any network call is made. Nothing is saved here: the user confirms and edits the
 * candidate first.
 */
class AddBookFromIsbn(
    private val books: BookRepository,
    private val metadata: MetadataRepository,
) {
    suspend operator fun invoke(isbn: String): Try<BookCandidate> =
        ValidateIsbn13(isbn).flatMap { isbn13 ->
            ensureNotInLibrary(isbn13).flatMap { metadata.lookupByIsbn(isbn13) }
        }

    private suspend fun ensureNotInLibrary(isbn13: String): Try<Unit> =
        books.findByIsbn13(isbn13).flatMap { existing ->
            if (existing == null) {
                Try.Success(Unit)
            } else {
                Try.Failure(AppError.Duplicate(existing.id))
            }
        }
}
