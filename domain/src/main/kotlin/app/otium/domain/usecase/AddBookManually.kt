package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.Book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository

/**
 * Validates a candidate and saves it as a new book: title required, ISBN (if given)
 * checked and normalized to ISBN-13, duplicates rejected with the existing book's id.
 */
class AddBookManually(private val repository: BookRepository, private val clock: Clock) {
    suspend operator fun invoke(candidate: BookCandidate): Try<Long> {
        if (candidate.title.isBlank()) return Try.Failure(AppError.InvalidInput("title"))

        return resolveIsbn(candidate).flatMap { isbn13 ->
            ensureNotDuplicate(isbn13).flatMap { repository.add(toBook(candidate, isbn13)) }
        }
    }

    /** The normalized ISBN-13, or null when the candidate has no ISBN at all. */
    private fun resolveIsbn(candidate: BookCandidate): Try<String?> {
        val input = candidate.isbn13?.takeIf(String::isNotBlank)
            ?: candidate.isbn10?.takeIf(String::isNotBlank)
            ?: return Try.Success(null)
        return ValidateIsbn13(input)
    }

    private suspend fun ensureNotDuplicate(isbn13: String?): Try<Unit> {
        if (isbn13 == null) return Try.Success(Unit)
        return repository.findByIsbn13(isbn13).flatMap { existing ->
            if (existing != null) {
                Try.Failure(AppError.Duplicate(existing.id))
            } else {
                Try.Success(Unit)
            }
        }
    }

    private fun toBook(candidate: BookCandidate, isbn13: String?): Book {
        val now = clock.nowMillis()
        return Book(
            isbn13 = isbn13,
            isbn10 = candidate.isbn10?.let(::normalizeIsbn10),
            title = candidate.title.trim(),
            subtitle = candidate.subtitle?.trim()?.takeIf(String::isNotEmpty),
            authors = candidate.authors.map(String::trim).filter(String::isNotEmpty),
            publisher = candidate.publisher,
            publishedYear = candidate.publishedYear,
            language = candidate.language,
            description = candidate.description,
            totalPages = candidate.totalPages?.coerceAtLeast(0) ?: 0,
            categories = candidate.categories,
            tags = candidate.tags,
            coverPath = null,
            coverSourceUrl = candidate.coverUrl,
            source = candidate.source,
            addedAt = now,
            updatedAt = now,
            userEdited = candidate.source == BookSource.MANUAL,
        )
    }

    private fun normalizeIsbn10(value: String): String? =
        value.filter { it != '-' && !it.isWhitespace() }.uppercase().takeIf(String::isNotEmpty)
}
