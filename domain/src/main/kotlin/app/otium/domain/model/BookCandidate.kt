package app.otium.domain.model

/**
 * Normalized book data before it is saved: the output of a metadata lookup or of the
 * confirm/edit screen. [totalPages] is null when unknown.
 */
data class BookCandidate(
    val isbn13: String? = null,
    val isbn10: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val language: String? = null,
    val description: String? = null,
    val totalPages: Int? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val coverUrl: String? = null,
    /** Other covers the services offer for this book, which the user can pick instead. */
    val alternateCoverUrls: List<String> = emptyList(),
    val source: BookSource,
)
