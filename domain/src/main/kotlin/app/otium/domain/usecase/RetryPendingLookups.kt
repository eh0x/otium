package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookStatus
import app.otium.domain.port.Clock
import app.otium.domain.port.CoverStore
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository

/** How a run of [RetryPendingLookups] went. [remaining] books are worth trying again later. */
data class PendingLookups(val resolved: Int, val remaining: Int)

/**
 * Looks up the books [SaveIsbnForLater] kept while offline and fills in their details. Progress,
 * tags and the date added are kept. A book the user has edited in the meantime is never
 * overwritten, and a book no service knows stops waiting. Lookups that could not reach the
 * services stay pending and are counted in [PendingLookups.remaining]. The first such failure
 * ends the run, so a large import does not keep asking a service that is rate limiting or out of
 * reach; the scheduler tries again later. A book that was finished stays finished when the
 * lookup changes its page count.
 */
class RetryPendingLookups(
    private val books: BookRepository,
    private val metadata: MetadataRepository,
    private val covers: CoverStore,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Try<PendingLookups> {
        val pending = when (val result = books.getBooksNeedingLookup()) {
            is Try.Success -> result.value
            is Try.Failure -> return result
        }
        var resolved = 0
        for (book in pending) {
            when (retry(book)) {
                Outcome.RESOLVED -> resolved++
                Outcome.WAITING -> Unit
                Outcome.UNREACHABLE -> break
            }
        }
        return Try.Success(PendingLookups(resolved, pending.size - resolved))
    }

    private enum class Outcome { RESOLVED, WAITING, UNREACHABLE }

    private suspend fun retry(book: Book): Outcome {
        val isbn13 = book.isbn13
        if (book.userEdited || isbn13 == null) return stopWaiting(book).toOutcome()

        return when (val result = metadata.lookupByIsbn(isbn13)) {
            is Try.Success -> fillIn(book, result.value).toOutcome()

            is Try.Failure -> when (result.error) {
                AppError.NotFound -> stopWaiting(book).toOutcome()
                AppError.Offline, AppError.RateLimited, AppError.Timeout -> Outcome.UNREACHABLE
                else -> Outcome.WAITING
            }
        }
    }

    private fun Boolean.toOutcome() = if (this) Outcome.RESOLVED else Outcome.WAITING

    private suspend fun stopWaiting(book: Book): Boolean =
        books.update(book.copy(needsLookup = false, updatedAt = clock.nowMillis())) is Try.Success

    private suspend fun fillIn(book: Book, found: BookCandidate): Boolean {
        val coverPath = found.coverUrl?.let { url ->
            (covers.saveFromUrl(url) as? Try.Success)?.value
        }
        val totalPages = found.totalPages?.takeIf { it > 0 } ?: book.totalPages
        val updated = book.copy(
            isbn10 = found.isbn10?.let(ValidateIsbn13::isbn10OrNull) ?: book.isbn10,
            title = found.title.ifBlank { book.title },
            subtitle = found.subtitle,
            series = book.series ?: found.series,
            seriesIndex = if (book.series != null || book.seriesIndex != null) {
                book.seriesIndex
            } else {
                found.seriesIndex
            },
            authors = found.authors,
            publisher = found.publisher,
            publishedYear = found.publishedYear,
            language = found.language,
            description = found.description,
            totalPages = totalPages,
            currentPage = if (book.status == BookStatus.READ) {
                totalPages
            } else {
                ClampPage(book.currentPage, totalPages)
            },
            categories = found.categories.ifEmpty { book.categories },
            tags = (book.tags + found.tags).distinct(),
            coverPath = coverPath ?: book.coverPath,
            coverSourceUrl = found.coverUrl ?: book.coverSourceUrl,
            source = found.source,
            updatedAt = clock.nowMillis(),
            needsLookup = false,
        )
        // The book store deletes the cover again if this fails, or the one it replaces.
        return books.update(updated) is Try.Success
    }
}
