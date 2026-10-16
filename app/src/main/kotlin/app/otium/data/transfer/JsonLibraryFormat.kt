package app.otium.data.transfer

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookSource
import app.otium.domain.model.LibraryEntry
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.domain.port.LibraryJson
import app.otium.domain.transfer.ImportLoan
import app.otium.domain.transfer.ImportNote
import app.otium.domain.transfer.ImportRow
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Otium's library file, version 3:
 *
 * ```json
 * {"format": "otium-library", "version": 3, "exportedAt": 1760227200000, "books": [{…}]}
 * ```
 *
 * Each book holds every field of [Book] except its id and cover file, a `notes` list of its notes
 * and quotes (`kind`, `text`, `page`, `createdAt`, `updatedAt`) in reading order, and a `loans`
 * list (`direction`, `counterparty`, `lentOn`, `remindOn`, `returnedOn`), oldest first. Times are
 * epoch milliseconds; the days of a loan are epoch days. Version 1 files have no notes and version
 * 2 files no loans; both are still read. Fields added later must be optional, so older files stay
 * readable; a file from a newer, incompatible version is refused rather than read in part, which
 * is why this one is version 3: an app that only knows version 2 says so instead of importing the
 * books and quietly leaving the loans behind.
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

    override fun write(entries: List<LibraryEntry>, exportedAt: Long): String = json.encodeToString(
        LibraryFileDto.serializer(),
        LibraryFileDto(FORMAT, VERSION, exportedAt, entries.map(LibraryEntry::toDto)),
    )

    private companion object {
        const val FORMAT = "otium-library"
        const val VERSION = 3

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
    val series: String? = null,
    val seriesIndex: Double? = null,
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
    val notes: List<NoteDto> = emptyList(),
    val loans: List<LoanDto> = emptyList(),
) {
    fun toRow() = ImportRow(
        title = title,
        authors = authors,
        isbn = isbn13 ?: isbn10,
        isbn10 = isbn10,
        subtitle = subtitle,
        series = series,
        seriesIndex = seriesIndex,
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
        notes = notes.map(NoteDto::toImportNote),
        loans = loans.map(LoanDto::toImportLoan),
    )
}

/** A kind this app does not know reads as a plain note, as the database does. */
@Serializable
private data class NoteDto(
    val kind: String = NoteKind.NOTE.name,
    val text: String = "",
    val page: Int? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
) {
    fun toImportNote() = ImportNote(
        kind = NoteKind.entries.firstOrNull { it.name == kind } ?: NoteKind.NOTE,
        text = text,
        page = page,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun Note.toDto() = NoteDto(
    kind = kind.name,
    text = text,
    page = page,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** A direction this app does not know reads as lent, as the database does. */
@Serializable
private data class LoanDto(
    val direction: String = LoanDirection.LENT.name,
    val counterparty: String = "",
    val lentOn: Long? = null,
    val remindOn: Long? = null,
    val returnedOn: Long? = null,
) {
    fun toImportLoan() = ImportLoan(
        direction = LoanDirection.entries.firstOrNull {
            it.name == direction
        } ?: LoanDirection.LENT,
        counterparty = counterparty,
        lentOn = lentOn,
        remindOn = remindOn,
        returnedOn = returnedOn,
    )
}

private fun Loan.toDto() = LoanDto(
    direction = direction.name,
    counterparty = counterparty,
    lentOn = lentOn,
    remindOn = remindOn,
    returnedOn = returnedOn,
)

private fun LibraryEntry.toDto() = book.toDto(notes.map(Note::toDto), loans.map(Loan::toDto))

private fun Book.toDto(notes: List<NoteDto>, loans: List<LoanDto>) = BookDto(
    title = title,
    subtitle = subtitle,
    series = series,
    seriesIndex = seriesIndex,
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
    notes = notes,
    loans = loans,
)
