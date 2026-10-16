package app.otium.domain.transfer

import app.otium.domain.model.BookFormat
import app.otium.domain.model.BookSource
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.NoteKind

/** The kinds of library file Otium reads. Goodreads is also the format of Otium's CSV export. */
enum class LibraryFormat { GOODREADS, STORYGRAPH, OTIUM }

/** The kinds of library file Otium writes. */
enum class ExportFormat { CSV, JSON }

/** Where a book stood in the app it came from. Otium shows it as progress (see `ImportLibrary`). */
enum class Shelf { READ, READING, TO_READ }

/**
 * One book read from a library file, before it is checked against the library. Only Otium's own
 * JSON carries every field; the other formats leave the rest empty. [isbn] may be an ISBN-10 or
 * ISBN-13 in any spelling, or anything else the file held there.
 */
data class ImportRow(
    val title: String,
    val authors: List<String> = emptyList(),
    val isbn: String? = null,
    val isbn10: String? = null,
    val subtitle: String? = null,
    val series: String? = null,
    val seriesIndex: Double? = null,
    val publisher: String? = null,
    val publishedYear: Int? = null,
    val language: String? = null,
    val description: String? = null,
    /** 0 means unknown. */
    val totalPages: Int = 0,
    /** The exact page, when the file knows it; otherwise progress comes from [shelf]. */
    val currentPage: Int? = null,
    val shelf: Shelf = Shelf.TO_READ,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val coverSourceUrl: String? = null,
    /** Where the details came from. Only Otium's own file knows; the rest count as typed in. */
    val source: BookSource = BookSource.MANUAL,
    /** Epoch milliseconds; null means "now". */
    val addedAt: Long? = null,
    /** True when the file already holds full details, so no lookup is needed. */
    val complete: Boolean = false,
    val userEdited: Boolean = false,
    /** How the owner has the book, when the file says (Otium's own file, or a Goodreads Binding). */
    val format: BookFormat? = null,
    /** The book's notes. Only Otium's own file (version 2 and later) carries them. */
    val notes: List<ImportNote> = emptyList(),
    /** The book's loans. Only Otium's own file (version 3 and later) carries them. */
    val loans: List<ImportLoan> = emptyList(),
)

/**
 * One loan read from an Otium file, before it is checked: the name may be blank or too long, the
 * dates may not fit together, and a day of null means the file did not say. Days are epoch days.
 */
data class ImportLoan(
    val direction: LoanDirection,
    val counterparty: String,
    val lentOn: Long? = null,
    val remindOn: Long? = null,
    val returnedOn: Long? = null,
)

/**
 * One note or quote read from an Otium file, before it is checked: the text may be blank or too
 * long, and a time of null means the file did not say.
 */
data class ImportNote(
    val kind: NoteKind,
    val text: String,
    val page: Int? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)
