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
    /** The shelf the book stands on: its first category, or null when it has none. */
    val shelf: String? = null,
    /** Epoch milliseconds of the last change, such as progress. */
    val updatedAt: Long = 0L,
    val series: String? = null,
    val seriesIndex: Double? = null,
)
