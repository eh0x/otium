package app.otium.domain.model

import app.otium.domain.usecase.DeriveStatus

/**
 * How far the owner is through the books of one series that are in the library. The library
 * cannot know how long a series really is, so [books] counts only the books it holds.
 *
 * [fraction] is the average of each book's progress: a read book counts as 1, a book in progress
 * as its share of pages read, and a book not started or without a page count as 0. It must stay in
 * sync with the series progress SQL in the library DAO.
 */
data class SeriesProgress(val name: String, val books: Int, val read: Int, val fraction: Double) {
    companion object {
        fun of(name: String, books: List<BookSummary>): SeriesProgress = SeriesProgress(
            name = name,
            books = books.size,
            read = books.count { DeriveStatus(it.currentPage, it.totalPages) == BookStatus.READ },
            fraction = if (books.isEmpty()) {
                0.0
            } else {
                books.sumOf { bookProgress(it.currentPage, it.totalPages) } / books.size
            },
        )

        /** One book's share read, from 0 to 1; 0 when the page count is unknown. */
        fun bookProgress(currentPage: Int, totalPages: Int): Double =
            if (currentPage <= 0 || totalPages <= 0) {
                0.0
            } else {
                minOf(1.0, currentPage.toDouble() / totalPages)
            }
    }
}
