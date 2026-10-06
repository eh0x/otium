package app.otium.domain.fakes

import app.otium.domain.model.Book
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.SortOrder

/**
 * Approximates the real library query: text, status, any-of categories and tags, author, and
 * sorting. The Room tests are authoritative; this is enough for ViewModel behavior.
 */
internal fun Book.matches(filter: LibraryFilter): Boolean {
    val text = filter.query.lowercase()
    val textMatches = text.isEmpty() ||
        title.lowercase().contains(text) ||
        authors.any { it.lowercase().contains(text) }
    return textMatches &&
        (filter.status == null || status == filter.status) &&
        (filter.categories.isEmpty() || categories.any { it in filter.categories }) &&
        (filter.tags.isEmpty() || tags.any { it in filter.tags }) &&
        (filter.author == null || filter.author in authors)
}

internal fun SortOrder.comparator(): Comparator<Book> = when (this) {
    SortOrder.TITLE -> compareBy { it.title.lowercase() }

    SortOrder.AUTHOR -> compareBy { it.authors.joinToString().lowercase() }

    SortOrder.RECENTLY_ADDED -> compareByDescending { it.addedAt }

    SortOrder.LAST_UPDATED -> compareByDescending { it.updatedAt }

    SortOrder.PROGRESS -> compareByDescending {
        if (it.totalPages > 0) it.currentPage.toDouble() / it.totalPages else 0.0
    }
}
