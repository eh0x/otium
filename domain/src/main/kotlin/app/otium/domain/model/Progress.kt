package app.otium.domain.model

/** Result of a progress update: the stored (clamped) page and the derived status. */
data class Progress(val currentPage: Int, val totalPages: Int, val status: BookStatus)
