package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.error.map
import app.otium.domain.model.Progress
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository

/**
 * Sets a book's total page count, for example when a lookup left it unknown. The current page is
 * clamped to the new total, and the book is marked as user edited so a later metadata refresh
 * does not overwrite the value.
 */
class SetTotalPages(private val repository: BookRepository, private val clock: Clock) {
    suspend operator fun invoke(bookId: Long, totalPages: Int): Try<Progress> {
        if (totalPages <= 0) return Try.Failure(AppError.InvalidInput("totalPages"))

        return repository.getBook(bookId).flatMap { book ->
            val currentPage = ClampPage(book.currentPage, totalPages)
            val updated = book.copy(
                totalPages = totalPages,
                currentPage = currentPage,
                updatedAt = clock.nowMillis(),
                userEdited = true,
            )
            repository.update(updated).map {
                Progress(currentPage, totalPages, DeriveStatus(currentPage, totalPages))
            }
        }
    }
}
