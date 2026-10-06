package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.Book
import app.otium.domain.model.BookSource
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository

/**
 * Keeps an ISBN the user could not look up, usually because the device is offline. The book is
 * saved straight away with only its ISBN, which also stands in for the title, and is marked as
 * waiting for a lookup; [RetryPendingLookups] fills in the details later.
 */
class SaveIsbnForLater(private val books: BookRepository, private val clock: Clock) {
    suspend operator fun invoke(isbn: String): Try<Long> = ValidateIsbn13(isbn).flatMap { isbn13 ->
        books.findByIsbn13(isbn13).flatMap { existing ->
            if (existing != null) {
                Try.Failure(AppError.Duplicate(existing.id))
            } else {
                books.add(placeholder(isbn13))
            }
        }
    }

    private fun placeholder(isbn13: String): Book {
        val now = clock.nowMillis()
        return Book(
            isbn13 = isbn13,
            isbn10 = null,
            title = isbn13,
            subtitle = null,
            authors = emptyList(),
            publisher = null,
            publishedYear = null,
            language = null,
            description = null,
            totalPages = 0,
            categories = emptyList(),
            tags = emptyList(),
            coverPath = null,
            coverSourceUrl = null,
            source = BookSource.MANUAL,
            addedAt = now,
            updatedAt = now,
            needsLookup = true,
        )
    }
}
