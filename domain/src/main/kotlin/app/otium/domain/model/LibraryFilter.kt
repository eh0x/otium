package app.otium.domain.model

/**
 * Library query. Different filters combine with AND. Within [categories] and within [tags]
 * a book matches when it has any of the selected values.
 */
data class LibraryFilter(
    val query: String = "",
    val status: BookStatus? = null,
    val categories: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val author: String? = null,
    val sort: SortOrder = SortOrder.TITLE,
)

enum class SortOrder { TITLE, AUTHOR, RECENTLY_ADDED, LAST_UPDATED, PROGRESS }
