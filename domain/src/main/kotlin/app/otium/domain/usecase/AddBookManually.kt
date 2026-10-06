package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository

/**
 * Validates a candidate and saves it as a new book: title required, ISBN (if given)
 * checked and normalized to ISBN-13, duplicates rejected with the existing book's id.
 */
class AddBookManually(
    private val repository: BookRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(candidate: BookCandidate): Try<Long> {
        if (candidate.title.isBlank()) return Try.Failure(AppError.InvalidInput("title"))

        val isbnInput = candidate.isbn13?.takeIf(String::isNotBlank) ?: candidate.isbn10?.takeIf(String::isNotBlank)
        val isbn13 = if (isbnInput == null) {
            null
        } else {
            when (val validated = ValidateIsbn13(isbnInput)) {
                is Try.Success -> validated.value
                is Try.Failure -> return validated
            }
        }

        if (isbn13 != null) {
            when (val existing = repository.findByIsbn13(isbn13)) {
                is Try.Failure -> return existing
                is Try.Success -> existing.value?.let { return Try.Failure(AppError.Duplicate(it.id)) }
            }
        }

        val now = clock.nowMillis()
        return repository.add(
            Book(
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
            ),
        )
    }

    private fun normalizeIsbn10(value: String): String? =
        value.filter { it != '-' && !it.isWhitespace() }.uppercase().takeIf(String::isNotEmpty)
}
