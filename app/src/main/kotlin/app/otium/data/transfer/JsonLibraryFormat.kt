package app.otium.data.transfer

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookSource
import app.otium.domain.port.LibraryJson
import app.otium.domain.transfer.ImportRow
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Otium's library file, version 1:
 *
 * ```json
 * {"format": "otium-library", "version": 1, "exportedAt": 1760227200000, "books": [{…}]}
 * ```
 *
 * Each book holds every field of [Book] except its id and cover file; times are epoch
 * milliseconds. Fields added later must be optional, so older files stay readable; a file from a
 * newer, incompatible version is refused rather than read in part.
 */
class JsonLibraryFormat @Inject constructor() : LibraryJson {

    override fun read(text: String): Try<List<ImportRow>> {
        val file = try {
            json.decodeFromString<LibraryFileDto>(text)
        } catch (ignored: SerializationException) {
            return Try.Failure(AppError.UnknownFileFormat)
        } catch (ignored: IllegalArgumentException) {
            return Try.Failure(AppError.UnknownFileFormat)
        }
        return when {
            file.format != FORMAT -> Try.Failure(AppError.UnknownFileFormat)

            file.version > VERSION -> Try.Failure(
                AppError.IncompatibleBackup(file.version, VERSION),
            )

            else -> Try.Success(file.books.map(BookDto::toRow))
        }
    }

    override fun write(books: List<Book>, exportedAt: Long): String = json.encodeToString(
        LibraryFileDto.serializer(),
        LibraryFileDto(FORMAT, VERSION, exportedAt, books.map(Book::toDto)),
    )

    private companion object {
        const val FORMAT = "otium-library"
        const val VERSION = 1

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            prettyPrint = true
        }
    }
}

@Serializable
private data class LibraryFileDto(
    val format: String,
    val version: Int,
    val exportedAt: Long = 0L,
    val books: List<BookDto> = emptyList(),
)

@Serializable
private data class BookDto(
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val isbn13: String? = null,
    val isbn10: String? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val language: String? = null,
    val description: String? = null,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val coverSourceUrl: String? = null,
    val source: String? = null,
    val addedAt: Long? = null,
    val updatedAt: Long? = null,
    val userEdited: Boolean = false,
    val needsLookup: Boolean = false,
) {
    fun toRow() = ImportRow(
        title = title,
        authors = authors,
        isbn = isbn13 ?: isbn10,
        isbn10 = isbn10,
        subtitle = subtitle,
        publisher = publisher,
        publishedYear = publishedYear,
        language = language,
        description = description,
        totalPages = totalPages,
        currentPage = currentPage,
        categories = categories,
        tags = tags,
        coverSourceUrl = coverSourceUrl,
        source = BookSource.entries.firstOrNull { it.name == source } ?: BookSource.MANUAL,
        addedAt = addedAt,
        complete = !needsLookup,
        userEdited = userEdited,
    )
}

private fun Book.toDto() = BookDto(
    title = title,
    subtitle = subtitle,
    authors = authors,
    isbn13 = isbn13,
    isbn10 = isbn10,
    publisher = publisher,
    publishedYear = publishedYear,
    language = language,
    description = description,
    totalPages = totalPages,
    currentPage = currentPage,
    categories = categories,
    tags = tags,
    coverSourceUrl = coverSourceUrl,
    source = source.name,
    addedAt = addedAt,
    updatedAt = updatedAt,
    userEdited = userEdited,
    needsLookup = needsLookup,
)
