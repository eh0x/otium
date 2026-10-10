package app.otium.data.local.mapper

import app.otium.data.cover.CoverFiles
import app.otium.data.local.entity.BookEntity
import app.otium.data.local.row.BookListRow
import app.otium.domain.model.Book
import app.otium.domain.model.BookSource
import app.otium.domain.model.BookSummary
import app.otium.domain.usecase.DeriveStatus

private const val AUTHOR_SEPARATOR = ", "

fun BookEntity.toDomain(
    authors: List<String>,
    categories: List<String>,
    tags: List<String>,
    covers: CoverFiles,
) = Book(
    id = id,
    isbn13 = isbn13,
    isbn10 = isbn10,
    title = title,
    subtitle = subtitle,
    authors = authors,
    publisher = publisher,
    publishedYear = publishedYear,
    language = language,
    description = description,
    totalPages = totalPages,
    currentPage = currentPage,
    categories = categories,
    tags = tags,
    coverPath = covers.pathOf(coverPath),
    coverSourceUrl = coverSourceUrl,
    source = BookSource.entries.firstOrNull { it.name == source } ?: BookSource.MANUAL,
    addedAt = addedAt,
    updatedAt = updatedAt,
    userEdited = userEdited,
    needsLookup = needsLookup,
)

fun Book.toEntity(covers: CoverFiles) = BookEntity(
    id = id,
    isbn13 = isbn13,
    isbn10 = isbn10,
    title = title,
    subtitle = subtitle,
    authors = authors.joinToString(AUTHOR_SEPARATOR),
    publisher = publisher,
    publishedYear = publishedYear,
    language = language,
    description = description,
    totalPages = totalPages,
    currentPage = currentPage,
    coverPath = covers.nameOf(coverPath),
    coverSourceUrl = coverSourceUrl,
    source = source.name,
    addedAt = addedAt,
    updatedAt = updatedAt,
    userEdited = userEdited,
    needsLookup = needsLookup,
)

fun BookListRow.toSummary(covers: CoverFiles) = BookSummary(
    id = id,
    title = title,
    authors = authors,
    coverPath = covers.pathOf(coverPath),
    coverSourceUrl = coverSourceUrl,
    currentPage = currentPage,
    totalPages = totalPages,
    status = DeriveStatus(currentPage, totalPages),
    shelf = shelf,
    updatedAt = updatedAt,
)
