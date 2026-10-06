package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.Progress
import app.otium.domain.repo.BookRepository

/**
 * Marks a book as read by moving it to its last page. A book with no known total has no last
 * page, so it is refused instead of guessed.
 */
class MarkFinished(
    private val repository: BookRepository,
    private val updateProgress: UpdateProgress,
) {
    suspend operator fun invoke(bookId: Long): Try<Progress> =
        repository.getBook(bookId).flatMap { book ->
            if (book.totalPages > 0) {
                updateProgress(book.id, book.totalPages)
            } else {
                Try.Failure(AppError.InvalidInput("totalPages"))
            }
        }
}
