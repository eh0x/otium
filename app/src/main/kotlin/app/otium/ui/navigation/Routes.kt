package app.otium.ui.navigation

import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. The detail screen reads its arguments from [DetailRoute]. */
@Serializable
data object LibraryRoute

@Serializable
data class DetailRoute(val bookId: Long)

/** Adding a book by scanning its barcode. */
@Serializable
data object ScanRoute

/** Adding a book by typing its ISBN. */
@Serializable
data object IsbnEntryRoute

/** Adding a book by searching its title or author. */
@Serializable
data object TitleSearchRoute

/**
 * The confirm/edit screen. With a [bookId] it edits that book; otherwise it confirms a new book
 * from the remaining fields, which carry a looked-up or empty candidate. The fields are flat so
 * the draft survives process death in the back stack. Zero stands for an unknown year or page
 * count, because route arguments cannot be nullable numbers.
 */
@Serializable
data class ConfirmRoute(
    val bookId: Long = NEW_BOOK,
    val isbn13: String? = null,
    val isbn10: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedYear: Int = 0,
    val language: String? = null,
    val description: String? = null,
    val totalPages: Int = 0,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val coverUrl: String? = null,
    val alternateCoverUrls: List<String> = emptyList(),
    /** A [BookSource] name. Kept as text so R8 has no domain enum serializer to keep. */
    val source: String = BookSource.MANUAL.name,
) {
    companion object {
        const val NEW_BOOK = 0L

        fun edit(bookId: Long) = ConfirmRoute(bookId = bookId)

        fun from(candidate: BookCandidate) = ConfirmRoute(
            isbn13 = candidate.isbn13,
            isbn10 = candidate.isbn10,
            title = candidate.title,
            subtitle = candidate.subtitle,
            authors = candidate.authors,
            publisher = candidate.publisher,
            publishedYear = candidate.publishedYear ?: 0,
            language = candidate.language,
            description = candidate.description,
            totalPages = candidate.totalPages ?: 0,
            categories = candidate.categories,
            tags = candidate.tags,
            coverUrl = candidate.coverUrl,
            alternateCoverUrls = candidate.alternateCoverUrls,
            source = candidate.source.name,
        )
    }

    val isEdit: Boolean get() = bookId != NEW_BOOK

    fun toCandidate() = BookCandidate(
        isbn13 = isbn13,
        isbn10 = isbn10,
        title = title,
        subtitle = subtitle,
        authors = authors,
        publisher = publisher,
        publishedYear = publishedYear.takeIf { it > 0 },
        language = language,
        description = description,
        totalPages = totalPages.takeIf { it > 0 },
        categories = categories,
        tags = tags,
        coverUrl = coverUrl,
        alternateCoverUrls = alternateCoverUrls,
        source = BookSource.entries.firstOrNull { it.name == source } ?: BookSource.MANUAL,
    )
}

/** Backing up the library to Google Drive and restoring it. */
@Serializable
data object BackupRoute

/** Debug builds only: the theme catalog. */
@Serializable
data object CatalogRoute
