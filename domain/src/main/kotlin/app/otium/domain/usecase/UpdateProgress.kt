package app.otium.domain.usecase

import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.error.map
import app.otium.domain.model.Progress
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository

/** Sets a book's current page, clamped to its total, and reports the derived status. */
class UpdateProgress(private val repository: BookRepository, private val clock: Clock) {
    suspend operator fun invoke(bookId: Long, page: Int): Try<Progress> =
        repository.getBook(bookId).flatMap { book ->
            val clamped = ClampPage(page, book.totalPages)
            repository.updateProgress(book.id, clamped, clock.nowMillis()).map {
                Progress(
                    currentPage = clamped,
                    totalPages = book.totalPages,
                    status = DeriveStatus(clamped, book.totalPages),
                )
            }
        }
}
