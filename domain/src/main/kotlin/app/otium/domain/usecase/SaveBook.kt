package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
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
 * A remote cover is downloaded here, once. A cover that cannot be downloaded never stops the
 * save: the book is kept without one.
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
    suspend fun add(candidate: BookCandidate, cover: CoverChoice, edited: Boolean): Try<Long> {
        if (candidate.title.isBlank()) return Try.Failure(AppError.InvalidInput("title"))

        return resolveIsbn(candidate).flatMap { isbn13 ->
            ensureIsbnFree(isbn13, ownerId = null).flatMap {
                val stored = store(cover)
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
                books.add(book).also { if (it is Try.Failure) discard(stored) }
            }
        }
    }

    /**
     * Replaces the details of book [bookId] with [candidate]. Reading progress and the date the
     * book was added are kept; the current page is clamped to a lowered total. The book counts as
     * edited by the user from now on and stops waiting for a lookup.
     */
    suspend fun update(bookId: Long, candidate: BookCandidate, cover: CoverChoice): Try<Unit> {
        if (candidate.title.isBlank()) return Try.Failure(AppError.InvalidInput("title"))

        return books.getBook(bookId).flatMap { existing ->
            resolveIsbn(candidate).flatMap { isbn13 ->
                ensureIsbnFree(isbn13, ownerId = bookId).flatMap {
                    val stored = if (cover == CoverChoice.Unchanged) {
                        StoredCover(existing.coverPath, existing.coverSourceUrl, downloaded = false)
                    } else {
                        store(cover)
                    }
                    val details = existing.withDetailsOf(candidate, isbn13)
                    val updated = details.copy(
                        currentPage = ClampPage(existing.currentPage, details.totalPages),
                        coverPath = stored.path,
                        coverSourceUrl = stored.sourceUrl,
                        updatedAt = clock.nowMillis(),
                        userEdited = true,
                        needsLookup = false,
                    )
                    books.update(updated).also { result ->
                        when (result) {
                            is Try.Success -> replaceOldCover(existing.coverPath, stored.path)
                            is Try.Failure -> discard(stored)
                        }
                    }
                }
            }
        }
    }

    private data class StoredCover(
        val path: String?,
        val sourceUrl: String?,
        val downloaded: Boolean,
    )

    private suspend fun store(cover: CoverChoice): StoredCover = when (cover) {
        CoverChoice.None, CoverChoice.Unchanged -> StoredCover(null, null, downloaded = false)

        is CoverChoice.Stored -> StoredCover(cover.path, null, downloaded = false)

        is CoverChoice.Remote -> when (val result = covers.saveFromUrl(cover.url)) {
            is Try.Success -> StoredCover(result.value, cover.url, downloaded = true)
            is Try.Failure -> StoredCover(null, cover.url, downloaded = false)
        }
    }

    /** Removes a cover this use case downloaded for a save that then failed. */
    private suspend fun discard(stored: StoredCover) {
        if (stored.downloaded && stored.path != null) covers.delete(stored.path)
    }

    private suspend fun replaceOldCover(oldPath: String?, newPath: String?) {
        if (oldPath != null && oldPath != newPath) covers.delete(oldPath)
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
        isbn10 = candidate.isbn10?.let(::normalizeIsbn10),
        title = candidate.title.trim(),
        subtitle = candidate.subtitle.cleaned(),
        authors = candidate.authors.map(String::trim).filter(String::isNotEmpty),
        publisher = candidate.publisher.cleaned(),
        publishedYear = candidate.publishedYear,
        language = candidate.language.cleaned(),
        description = candidate.description.cleaned(),
        totalPages = candidate.totalPages?.coerceAtLeast(0) ?: 0,
        categories = candidate.categories,
        tags = candidate.tags,
    )

    private fun String?.cleaned(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun normalizeIsbn10(value: String): String? =
        value.filter { it != '-' && !it.isWhitespace() }.uppercase().takeIf(String::isNotEmpty)
}
