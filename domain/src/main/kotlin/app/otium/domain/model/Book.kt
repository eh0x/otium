package app.otium.domain.model

import app.otium.domain.usecase.DeriveStatus

/** A book in the library. Timestamps are epoch milliseconds. */
data class Book(
    val id: Long = 0L,
    val isbn13: String?,
    val isbn10: String?,
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val publisher: String?,
    val publishedYear: Int?,
    val language: String?,
    val description: String?,
    /** 0 means unknown. */
    val totalPages: Int,
    val currentPage: Int = 0,
    val categories: List<String>,
    val tags: List<String>,
    val coverPath: String?,
    val coverSourceUrl: String?,
    val source: BookSource,
    val addedAt: Long,
    val updatedAt: Long,
    /** When true, a later metadata refresh must not overwrite this book's fields. */
    val userEdited: Boolean = false,
    /** True for ISBN-only entries saved offline that still await a metadata lookup. */
    val needsLookup: Boolean = false,
) {
    val status: BookStatus get() = DeriveStatus(currentPage, totalPages)
}
