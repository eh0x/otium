package app.otium.data.local.row

/** Room projection for library lists. Mapped to the domain BookSummary by the repository. */
data class BookListRow(
    val id: Long,
    val title: String,
    val authors: String,
    val coverPath: String?,
    val coverSourceUrl: String?,
    val currentPage: Int,
    val totalPages: Int,
)
