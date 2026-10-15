package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.error.map
import app.otium.domain.model.Book
import app.otium.domain.model.Progress
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository

/**
 * Tracks how far a book has been read. Every change keeps the current page within the book's
 * total ([ClampPage]) and reports the status that follows from it ([DeriveStatus]); the status is
 * never stored (design decision D5).
 */
class ReadingTracker(private val repository: BookRepository, private val clock: Clock) {

    /** Moves a book to [page], clamped to its total. */
    suspend fun setPage(bookId: Long, page: Int): Try<Progress> =
        repository.getBook(bookId).flatMap { book -> moveTo(book, page) }

    /**
     * Marks a book as read by moving it to its last page. A book with no known total has no last
     * page, so it is refused instead of guessed.
     */
    suspend fun finish(bookId: Long): Try<Progress> = repository.getBook(bookId).flatMap { book ->
        if (book.totalPages > 0) {
            moveTo(book, book.totalPages)
        } else {
            Try.Failure(AppError.InvalidInput("totalPages"))
        }
    }

    /**
     * Sets a book's total page count, for example when a lookup left it unknown. The current page
     * is clamped to the new total, and the book is marked as user edited so a later metadata
     * refresh does not overwrite the value.
     */
    suspend fun setTotalPages(bookId: Long, totalPages: Int): Try<Progress> {
        if (totalPages <= 0) return Try.Failure(AppError.InvalidInput("totalPages"))
        return repository.getBook(bookId).flatMap { book ->
            val currentPage = ClampPage(book.currentPage, totalPages)
            val updated = book.copy(
                totalPages = totalPages,
                currentPage = currentPage,
                updatedAt = clock.nowMillis(),
                userEdited = true,
            )
            repository.update(updated).map { progressOf(currentPage, totalPages) }
        }
    }

    private suspend fun moveTo(book: Book, page: Int): Try<Progress> {
        val clamped = ClampPage(page, book.totalPages)
        return repository.updateProgress(book.id, clamped, clock.nowMillis())
            .map { progressOf(clamped, book.totalPages) }
    }

    private fun progressOf(currentPage: Int, totalPages: Int) =
        Progress(currentPage, totalPages, DeriveStatus(currentPage, totalPages))
}
