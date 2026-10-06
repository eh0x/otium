package app.otium.domain.usecase

import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource

/**
 * Merges the Google Books and Open Library candidates for one ISBN into a single candidate.
 * Google wins; any field Google leaves empty is filled from Open Library.
 *
 * Covers are tried in order (Google, then Open Library) and the first one accepted by
 * [coverIsUsable] wins. Detecting placeholder images needs I/O, so the data layer supplies
 * that decision through the predicate.
 */
object MergeMetadata {
    operator fun invoke(
        google: BookCandidate?,
        openLibrary: BookCandidate?,
        coverIsUsable: (String) -> Boolean = { true },
    ): BookCandidate? {
        if (google == null && openLibrary == null) return null
        val sources = listOfNotNull(google, openLibrary)

        return BookCandidate(
            isbn13 = sources.firstNotNullOfOrNull { it.isbn13?.takeIf(String::isNotBlank) },
            isbn10 = sources.firstNotNullOfOrNull { it.isbn10?.takeIf(String::isNotBlank) },
            title = sources.firstNotNullOfOrNull { it.title.takeIf(String::isNotBlank) }.orEmpty(),
            subtitle = sources.firstNotNullOfOrNull { it.subtitle?.takeIf(String::isNotBlank) },
            authors = sources.firstOrNull { it.authors.isNotEmpty() }?.authors.orEmpty(),
            publisher = sources.firstNotNullOfOrNull { it.publisher?.takeIf(String::isNotBlank) },
            publishedYear = sources.firstNotNullOfOrNull { it.publishedYear },
            language = sources.firstNotNullOfOrNull { it.language?.takeIf(String::isNotBlank) },
            description = sources.firstNotNullOfOrNull { it.description?.takeIf(String::isNotBlank) },
            totalPages = sources.firstNotNullOfOrNull { it.totalPages?.takeIf { pages -> pages > 0 } },
            categories = sources.firstOrNull { it.categories.isNotEmpty() }?.categories.orEmpty(),
            tags = sources.flatMap { it.tags }.distinct(),
            coverUrl = sources.firstNotNullOfOrNull { it.coverUrl?.takeIf(coverIsUsable) },
            source = if (google != null) BookSource.GOOGLE else BookSource.OPEN_LIBRARY,
        )
    }
}
