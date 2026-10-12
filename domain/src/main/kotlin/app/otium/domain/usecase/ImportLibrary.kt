package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.Book
import app.otium.domain.port.Clock
import app.otium.domain.port.LookupScheduler
import app.otium.domain.repo.BookRepository
import app.otium.domain.transfer.ImportRow
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.transfer.Shelf

/** What importing a file would do, worked out before anything is written. */
data class ImportPreview(
    val format: LibraryFormat,
    /** The rows that have a title, in file order. */
    val rows: List<ImportRow>,
    /** Books that would be added, with or without an ISBN. */
    val newBooks: Int,
    /** Books whose ISBN is already in the library, or earlier in the file: left out. */
    val alreadyInLibrary: Int,
    /** New books without a usable ISBN: added as the file has them and never looked up. */
    val withoutIsbn: Int,
    /** Rows without a title: left out. */
    val unreadable: Int,
)

/** How an import went. [waitingForLookup] books get their details later, as D11 describes. */
data class ImportResult(
    val added: Int,
    val skipped: Int,
    val failed: Int,
    val waitingForLookup: Int,
)

/**
 * Brings a library exported from Goodreads, StoryGraph or Otium into this one, in two steps: a
 * [preview] the owner confirms, then the [import] itself. Books are added straight away from what
 * the file holds; a book with an ISBN, unless the file is Otium's own, also waits for a lookup
 * that fills in its cover and details later, as offline ISBN entries do (D11). Books already in
 * the library are left as they are, so importing the same file twice adds nothing.
 *
 * Progress comes from the shelf the book was on: read books are finished and books being read are
 * at their first page. Without a page count a shelf cannot show as progress, so it becomes a tag.
 */
class ImportLibrary(
    private val books: BookRepository,
    private val readFile: ReadLibraryFile,
    private val clock: Clock,
    private val scheduler: LookupScheduler,
    private val lock: BackupLock,
) {
    /** Reads the file at [uri] and works out what importing it would do. Nothing is written. */
    suspend fun preview(uri: String): Try<ImportPreview> =
        readFile(uri).flatMap { preview(it.format, it.rows) }

    /**
     * Adds the books of [preview]. A restore never runs at the same time. [onProgress] hears how
     * many rows are done after each one.
     */
    suspend fun import(
        preview: ImportPreview,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult = lock.withLock {
        val now = clock.nowMillis()
        var added = 0
        var skipped = 0
        var failed = 0
        var waiting = 0
        preview.rows.forEachIndexed { index, row ->
            val book = row.toBook(now)
            when (val result = books.add(book)) {
                is Try.Success -> {
                    added++
                    if (book.needsLookup) waiting++
                }

                is Try.Failure -> if (result.error is AppError.Duplicate) skipped++ else failed++
            }
            onProgress(index + 1, preview.rows.size)
        }
        if (waiting > 0) scheduler.schedule()
        ImportResult(added, skipped, failed, waiting)
    }

    private suspend fun preview(format: LibraryFormat, rows: List<ImportRow>): Try<ImportPreview> {
        val readable = rows.filter { it.title.isNotBlank() }
        val seen = HashSet<String>()
        var duplicates = 0
        var withoutIsbn = 0
        for (row in readable) {
            val isbn13 = row.validIsbn13()
            if (isbn13 == null) {
                withoutIsbn++
                continue
            }
            when (val existing = books.findByIsbn13(isbn13)) {
                is Try.Failure -> return existing
                is Try.Success -> if (existing.value != null || !seen.add(isbn13)) duplicates++
            }
        }
        return Try.Success(
            ImportPreview(
                format = format,
                rows = readable,
                newBooks = readable.size - duplicates,
                alreadyInLibrary = duplicates,
                withoutIsbn = withoutIsbn,
                unreadable = rows.size - readable.size,
            ),
        )
    }

    private fun ImportRow.toBook(now: Long): Book {
        val isbn13 = validIsbn13()
        return Book(
            isbn13 = isbn13,
            isbn10 = isbn10?.takeIf { it.length == ISBN10_LENGTH && ValidateIsbn13(it).isValid() },
            title = title.trim(),
            subtitle = subtitle,
            authors = authors,
            publisher = publisher,
            publishedYear = publishedYear,
            language = language,
            description = description,
            totalPages = totalPages.coerceAtLeast(0),
            currentPage = startPage(),
            categories = categories,
            tags = (tags + listOfNotNull(shelfTag())).distinct(),
            coverPath = null,
            coverSourceUrl = coverSourceUrl,
            source = source,
            addedAt = addedAt ?: now,
            updatedAt = now,
            userEdited = userEdited,
            needsLookup = isbn13 != null && !complete,
        )
    }

    /** The exact page when the file knows it; otherwise the shelf's, when pages are known. */
    private fun ImportRow.startPage(): Int = currentPage?.let { ClampPage(it, totalPages) }
        ?: when (shelf) {
            Shelf.READ -> totalPages.coerceAtLeast(0)
            Shelf.READING -> if (totalPages > 0) 1 else 0
            Shelf.TO_READ -> 0
        }

    /** The shelf as a tag, when the page count is unknown and progress cannot show it. */
    private fun ImportRow.shelfTag(): String? = when {
        currentPage != null || totalPages > 0 -> null
        shelf == Shelf.READ -> TAG_READ
        shelf == Shelf.READING -> TAG_READING
        else -> null
    }

    private fun ImportRow.validIsbn13(): String? =
        isbn?.let { (ValidateIsbn13(it) as? Try.Success)?.value }

    private fun Try<String>.isValid() = this is Try.Success

    private companion object {
        const val ISBN10_LENGTH = 10
        const val TAG_READ = "read"
        const val TAG_READING = "currently-reading"
    }
}
