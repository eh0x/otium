package app.otium.domain.transfer

import app.otium.domain.model.Book
import app.otium.domain.model.BookFormat
import app.otium.domain.model.BookStatus

/**
 * The library export Goodreads makes (My Books, Import and export, Export Library). Otium writes
 * its CSV export in the same columns, so StoryGraph and other apps that read Goodreads files can
 * read it too. Ratings, reviews and dates read have no place in Otium and are not read.
 */
object GoodreadsCsv {
    private const val TITLE = "Title"
    private const val AUTHOR = "Author"
    private const val AUTHOR_LAST_FIRST = "Author l-f"
    private const val ADDITIONAL_AUTHORS = "Additional Authors"
    private const val ISBN = "ISBN"
    private const val ISBN13 = "ISBN13"
    private const val PUBLISHER = "Publisher"
    private const val BINDING = "Binding"
    private const val PAGES = "Number of Pages"
    private const val YEAR = "Year Published"
    private const val ORIGINAL_YEAR = "Original Publication Year"
    private const val DATE_ADDED = "Date Added"
    private const val SHELVES = "Bookshelves"
    private const val EXCLUSIVE_SHELF = "Exclusive Shelf"
    private const val READ_COUNT = "Read Count"
    private const val OWNED_COPIES = "Owned Copies"

    private const val SHELF_READ = "read"
    private const val SHELF_READING = "currently-reading"
    private const val SHELF_TO_READ = "to-read"

    /** Every column of a Goodreads export, in its order. */
    val COLUMNS = listOf(
        "Book Id", TITLE, AUTHOR, AUTHOR_LAST_FIRST, ADDITIONAL_AUTHORS, ISBN, ISBN13, "My Rating",
        "Average Rating", PUBLISHER, BINDING, PAGES, YEAR, ORIGINAL_YEAR, "Date Read", DATE_ADDED,
        SHELVES, "Bookshelves with positions", EXCLUSIVE_SHELF, "My Review", "Spoiler",
        "Private Notes", READ_COUNT, OWNED_COPIES,
    )

    fun matches(table: CsvTable): Boolean = table.hasColumns(TITLE, AUTHOR, EXCLUSIVE_SHELF)

    fun read(table: CsvTable): List<ImportRow> = table.records.map { record ->
        val text = { column: String -> FileValues.fromSpreadsheetSafe(record[column]) }
        val exclusive = record[EXCLUSIVE_SHELF].lowercase()
        val shelf = when (exclusive) {
            SHELF_READ -> Shelf.READ
            SHELF_READING -> Shelf.READING
            else -> Shelf.TO_READ
        }
        // A custom exclusive shelf, such as "did-not-finish", is kept as a tag.
        val customShelf = exclusive.takeIf {
            it.isNotEmpty() && it != SHELF_READ && it != SHELF_READING && it != SHELF_TO_READ
        }
        ImportRow(
            title = text(TITLE),
            authors = (listOf(text(AUTHOR)) + FileValues.names(text(ADDITIONAL_AUTHORS)))
                .filter(String::isNotEmpty)
                .distinct(),
            isbn = FileValues.isbnOrNull(record[ISBN13]) ?: FileValues.isbnOrNull(record[ISBN]),
            isbn10 = FileValues.isbnOrNull(record[ISBN]),
            publisher = text(PUBLISHER).ifEmpty { null },
            publishedYear = FileValues.positiveIntOrNull(record[YEAR])
                ?: FileValues.positiveIntOrNull(record[ORIGINAL_YEAR]),
            totalPages = FileValues.positiveIntOrNull(record[PAGES]) ?: 0,
            shelf = shelf,
            tags = (FileValues.names(text(SHELVES)) + listOfNotNull(customShelf))
                .filterNot { it.lowercase() in setOf(SHELF_READ, SHELF_READING, SHELF_TO_READ) }
                .distinct(),
            addedAt = FileValues.parseDate(record[DATE_ADDED]),
            format = BookFormat.fromBinding(text(BINDING)),
        )
    }

    /**
     * [books] in Goodreads' columns. ISBNs are written as `="…"`, as Goodreads does, so that a
     * spreadsheet keeps their leading zeros. Categories and tags both become shelves, with spaces
     * turned into hyphens, since Goodreads shelf names have none. Text a book's owner typed (or a
     * service returned) is made [FileValues.spreadsheetSafe], so a title such as
     * `=HYPERLINK(…)` stays text when the file is opened in a spreadsheet.
     */
    fun write(books: List<Book>): String = Csv.write(listOf(COLUMNS) + books.map(::record))

    /** The columns that hold free text. The rest are ISBN guards, numbers, dates or fixed words. */
    private val TEXT_COLUMNS =
        setOf(TITLE, AUTHOR, AUTHOR_LAST_FIRST, ADDITIONAL_AUTHORS, PUBLISHER, SHELVES)

    private fun record(book: Book): List<String> {
        val values = mapOf(
            TITLE to book.title,
            AUTHOR to book.authors.firstOrNull().orEmpty(),
            AUTHOR_LAST_FIRST to book.authors.firstOrNull()?.let(::lastFirst).orEmpty(),
            ADDITIONAL_AUTHORS to book.authors.drop(1).joinToString(", "),
            ISBN to "=\"${book.isbn10.orEmpty()}\"",
            ISBN13 to "=\"${book.isbn13.orEmpty()}\"",
            PUBLISHER to book.publisher.orEmpty(),
            BINDING to book.format?.binding.orEmpty(),
            PAGES to book.totalPages.takeIf { it > 0 }?.toString().orEmpty(),
            YEAR to book.publishedYear?.toString().orEmpty(),
            DATE_ADDED to FileValues.formatDate(book.addedAt),
            SHELVES to (book.categories + book.tags)
                .map { it.trim().replace(Regex("""\s+"""), "-") }
                .distinct()
                .joinToString(", "),
            EXCLUSIVE_SHELF to when (book.status) {
                BookStatus.READ -> SHELF_READ
                BookStatus.IN_PROGRESS -> SHELF_READING
                BookStatus.NOT_STARTED -> SHELF_TO_READ
            },
            READ_COUNT to if (book.status == BookStatus.READ) "1" else "0",
            OWNED_COPIES to "1",
        )
        return COLUMNS.map { column ->
            val value = values[column].orEmpty()
            if (column in TEXT_COLUMNS) FileValues.spreadsheetSafe(value) else value
        }
    }

    /** "Ursula K. Le Guin" becomes "Guin, Ursula K. Le": a guess, as Goodreads' own is. */
    private fun lastFirst(name: String): String {
        val parts = name.trim().split(Regex("""\s+"""))
        if (parts.size < 2) return name.trim()
        return "${parts.last()}, ${parts.dropLast(1).joinToString(" ")}"
    }
}
