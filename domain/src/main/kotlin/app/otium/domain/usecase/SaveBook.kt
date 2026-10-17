package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.error.map
import app.otium.domain.model.Book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.model.CoverChoice
import app.otium.domain.port.Clock
import app.otium.domain.port.CoverStore
import app.otium.domain.repo.BookRepository

/**
 * Saves what the user confirmed on the confirm/edit screen, as a new book or over an existing
 * one. The title is required, an ISBN (if given) is checked and normalized to ISBN-13, and an
 * ISBN that belongs to another book is rejected with that book's id.
 *
 * The chosen cover is put in the cover store here, once. A remote cover that cannot be downloaded
 * never stops the save: the book is kept without one. The stored cover goes to the book store with
 * the book, which deletes it if the save fails and deletes the cover it replaces.
 */
class SaveBook(
    private val books: BookRepository,
    private val covers: CoverStore,
    private val clock: Clock,
) {
    /**
     * Adds [candidate] as a new book and returns its id. [edited] says whether the user changed
     * anything a lookup filled in; manual entries always count as edited, so a later metadata
     * refresh never overwrites them.
     */
    suspend fun add(candidate: BookCandidate, cover: CoverChoice, edited: Boolean): Try<Long> =
        prepare(candidate, cover, existing = null).flatMap { (isbn13, stored) ->
            val now = clock.nowMillis()
            val book = Book(
                isbn13 = isbn13,
                isbn10 = null,
                title = "",
                subtitle = null,
                authors = emptyList(),
                publisher = null,
                publishedYear = null,
                language = null,
                description = null,
                totalPages = 0,
                categories = emptyList(),
                tags = emptyList(),
                coverPath = stored.path,
                coverSourceUrl = stored.sourceUrl,
                source = candidate.source,
                addedAt = now,
                updatedAt = now,
                userEdited = edited || candidate.source == BookSource.MANUAL,
            ).withDetailsOf(candidate, isbn13)
            books.add(book)
        }

    /**
     * Replaces the details of book [bookId] with [candidate]. Reading progress and the date the
     * book was added are kept; the current page is clamped to a lowered total. The book counts as
     * edited by the user from now on and stops waiting for a lookup.
     */
    suspend fun update(bookId: Long, candidate: BookCandidate, cover: CoverChoice): Try<Unit> =
        books.getBook(bookId).flatMap { existing ->
            prepare(candidate, cover, existing).flatMap { (isbn13, stored) ->
                val details = existing.withDetailsOf(candidate, isbn13)
                val updated = details.copy(
                    currentPage = ClampPage(existing.currentPage, details.totalPages),
                    coverPath = stored.path,
                    coverSourceUrl = stored.sourceUrl,
                    updatedAt = clock.nowMillis(),
                    userEdited = true,
                    needsLookup = false,
                )
                books.update(updated)
            }
        }

    /** The checked ISBN-13 (null when there is none) and the stored cover. */
    private data class Prepared(val isbn13: String?, val cover: StoredCover)

    /** Checks the candidate against [existing] (null for a new book) and stores its cover. */
    private suspend fun prepare(
        candidate: BookCandidate,
        cover: CoverChoice,
        existing: Book?,
    ): Try<Prepared> {
        if (candidate.title.isBlank()) return Try.Failure(AppError.InvalidInput("title"))
        return resolveIsbn(candidate).flatMap { isbn13 ->
            ensureIsbnFree(isbn13, ownerId = existing?.id)
                .flatMap { store(cover, existing) }
                .map { Prepared(isbn13, it) }
        }
    }

    /** The cover a book ends up with. */
    private data class StoredCover(val path: String?, val sourceUrl: String?)

    /**
     * Puts the chosen cover in the store. A remote cover that cannot be downloaded leaves the book
     * without one; a picked photo that cannot be read fails, because the user chose it.
     */
    private suspend fun store(cover: CoverChoice, existing: Book?): Try<StoredCover> {
        val stored = when (cover) {
            CoverChoice.None -> StoredCover(null, null)

            CoverChoice.Unchanged -> StoredCover(existing?.coverPath, existing?.coverSourceUrl)

            is CoverChoice.Picked -> return covers.saveFromDevice(cover.uri)
                .map { StoredCover(it, null) }

            is CoverChoice.Remote -> when (val result = covers.saveFromUrl(cover.url)) {
                is Try.Success -> StoredCover(result.value, cover.url)
                is Try.Failure -> StoredCover(null, cover.url)
            }
        }
        return Try.Success(stored)
    }

    /** The normalized ISBN-13, or null when the candidate has no ISBN at all. */
    private fun resolveIsbn(candidate: BookCandidate): Try<String?> {
        val input = candidate.isbn13?.takeIf(String::isNotBlank)
            ?: candidate.isbn10?.takeIf(String::isNotBlank)
            ?: return Try.Success(null)
        return ValidateIsbn13(input)
    }

    /** Fails with `Duplicate` when a book other than [ownerId] already has [isbn13]. */
    private suspend fun ensureIsbnFree(isbn13: String?, ownerId: Long?): Try<Unit> {
        if (isbn13 == null) return Try.Success(Unit)
        return books.findByIsbn13(isbn13).flatMap { existing ->
            if (existing != null && existing.id != ownerId) {
                Try.Failure(AppError.Duplicate(existing.id))
            } else {
                Try.Success(Unit)
            }
        }
    }

    private fun Book.withDetailsOf(candidate: BookCandidate, isbn13: String?): Book = copy(
        isbn13 = isbn13,
        isbn10 = candidate.isbn10?.let(ValidateIsbn13::isbn10OrNull),
        title = candidate.title.trim(),
        subtitle = candidate.subtitle.cleaned(),
        series = candidate.series.cleaned(),
        seriesIndex = candidate.seriesIndex?.takeIf { it.isFinite() && it >= 0 },
        authors = candidate.authors.map(String::trim).filter(String::isNotEmpty),
        publisher = candidate.publisher.cleaned(),
        publishedYear = candidate.publishedYear,
        language = candidate.language.cleaned(),
        description = candidate.description.cleaned(),
        totalPages = candidate.totalPages?.coerceAtLeast(0) ?: 0,
        categories = candidate.categories,
        tags = candidate.tags,
        format = candidate.format,
    )

    private fun String?.cleaned(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
