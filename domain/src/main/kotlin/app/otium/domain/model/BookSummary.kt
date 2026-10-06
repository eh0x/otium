package app.otium.domain.model

/** Lightweight book row for library lists and the shelf grid. */
data class BookSummary(
    val id: Long,
    val title: String,
    /** Display string, authors joined for presentation. */
    val authors: String,
    val coverPath: String?,
    val coverSourceUrl: String?,
    val currentPage: Int,
    val totalPages: Int,
    val status: BookStatus,
)
