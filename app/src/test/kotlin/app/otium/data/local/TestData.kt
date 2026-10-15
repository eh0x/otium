package app.otium.data.local

import app.otium.domain.model.Book
import app.otium.domain.model.BookSource

fun testBook(
    title: String = "Title",
    subtitle: String? = null,
    authors: List<String> = listOf("Author"),
    isbn13: String? = null,
    categories: List<String> = emptyList(),
    tags: List<String> = emptyList(),
    totalPages: Int = 300,
    currentPage: Int = 0,
    addedAt: Long = 1L,
    updatedAt: Long = 1L,
    series: String? = null,
    seriesIndex: Double? = null,
) = Book(
    isbn13 = isbn13,
    isbn10 = null,
    title = title,
    subtitle = subtitle,
    series = series,
    seriesIndex = seriesIndex,
    authors = authors,
    publisher = "Publisher",
    publishedYear = 1999,
    language = "en",
    description = "Description",
    totalPages = totalPages,
    currentPage = currentPage,
    categories = categories,
    tags = tags,
    coverPath = null,
    coverSourceUrl = "https://example.org/cover.jpg",
    source = BookSource.MANUAL,
    addedAt = addedAt,
    updatedAt = updatedAt,
)
