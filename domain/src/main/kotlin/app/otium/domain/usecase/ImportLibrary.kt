package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.Book
import app.otium.domain.model.Loan
import app.otium.domain.model.Note
import app.otium.domain.port.Clock
import app.otium.domain.port.LookupScheduler
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.LoanRepository
import app.otium.domain.repo.NoteRepository
import app.otium.domain.transfer.ImportLoan
import app.otium.domain.transfer.ImportNote
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
    /** Notes and quotes that come with the new books; the ones that cannot be kept are left out. */
    val notes: Int = 0,
    /** Loans that come with the new books; the ones that cannot be kept are left out. */
    val loans: Int = 0,
)

/**
 * How an import went. [waitingForLookup] books get their details later, as D11 describes.
 * [notesSkipped] counts the notes of added books that could not be kept: blank, too long, with a
 * page below 1, or not saved. [loansSkipped] counts the loans that could not be kept: without a
 * usable name, with dates that do not fit, a second open loan on one book, or not saved.
 */
data class ImportResult(
    val added: Int,
    val skipped: Int,
    val failed: Int,
    val waitingForLookup: Int,
    val notesAdded: Int = 0,
    val notesSkipped: Int = 0,
    val loansAdded: Int = 0,
    val loansSkipped: Int = 0,
)

/**
 * Brings a library exported from Goodreads, StoryGraph or Otium into this one, in two steps: a
 * [preview] the owner confirms, then the [import] itself. Books are added straight away from what
 * the file holds; a book with an ISBN, unless the file is Otium's own, also waits for a lookup
 * that fills in its cover and details later, as offline ISBN entries do (D11). Books already in
 * the library are left as they are, with their notes, so importing the same file twice adds
 * nothing. The notes of an Otium file come with the books it adds, checked as the editor checks
 * them and keeping the times they were written. So do its loans, checked as the ledger checks
 * them; a book comes in with at most one open loan, and the reminders still ahead are planned.
 *
 * Progress comes from the shelf the book was on: read books are finished and books being read are
 * at their first page. Without a page count a shelf cannot show as progress, so it becomes a tag.
 */
// Every port an import writes to or arranges work through, so the class takes all of them;
// bundling them into a holder type would only hide the dependencies.
@Suppress("LongParameterList")
class ImportLibrary(
    private val books: BookRepository,
    private val notes: NoteRepository,
    private val loans: LoanRepository,
    private val readFile: ReadLibraryFile,
    private val clock: Clock,
    private val scheduler: LookupScheduler,
    private val loanReminders: SyncLoanReminders,
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
        val today = clock.todayEpochDay()
        var added = 0
        var skipped = 0
        var failed = 0
        var waiting = 0
        var notesAdded = 0
        var notesSkipped = 0
        var loansAdded = 0
        var loansSkipped = 0
        preview.rows.forEachIndexed { index, row ->
            val book = row.toBook(now)
            when (val result = books.add(book)) {
                is Try.Success -> {
                    added++
                    if (book.needsLookup) waiting++
                    val kept = addNotes(result.value, row.notes, now)
                    notesAdded += kept
                    notesSkipped += row.notes.size - kept
                    val lent = addLoans(result.value, row.loans, today)
                    loansAdded += lent
                    loansSkipped += row.loans.size - lent
                }

                is Try.Failure -> if (result.error is AppError.Duplicate) skipped++ else failed++
            }
            onProgress(index + 1, preview.rows.size)
        }
        if (waiting > 0) scheduler.schedule()
        if (loansAdded > 0) loanReminders()
        ImportResult(
            added,
            skipped,
            failed,
            waiting,
            notesAdded,
            notesSkipped,
            loansAdded,
            loansSkipped,
        )
    }

    /** Saves the acceptable loans of [rows] on book [bookId] and returns how many were saved. */
    private suspend fun addLoans(bookId: Long, rows: List<ImportLoan>, today: Long): Int =
        rows.acceptedAs(bookId, today).count { loans.add(it) is Try.Success }

    /**
     * The loans the ledger would keep: with a usable name and dates that fit, and at most one
     * open. A loan without a day it began is taken to have begun [today].
     */
    private fun List<ImportLoan>.acceptedAs(bookId: Long, today: Long): List<Loan> {
        var hasOpen = false
        return mapNotNull { row ->
            val name = Loan.cleanName(row.counterparty)
            val began = row.lentOn ?: today
            if (name == null || !Loan.areDatesValid(began, row.remindOn, row.returnedOn)) {
                return@mapNotNull null
            }
            val loan = Loan(0L, bookId, name, row.direction, began, row.remindOn, row.returnedOn)
            when {
                !loan.isOpen -> loan
                hasOpen -> null
                else -> loan.also { hasOpen = true }
            }
        }
    }

    /** Saves the acceptable notes of [rows] on book [bookId] and returns how many were saved. */
    private suspend fun addNotes(bookId: Long, rows: List<ImportNote>, now: Long): Int =
        rows.count { row ->
            val note = row.toNote(bookId, now)
            note != null && notes.add(note) is Try.Success
        }

    /** The note as the editor would keep it, or null when the editor would refuse it. */
    private fun ImportNote.toNote(bookId: Long, now: Long): Note? {
        val cleaned = Note.cleanText(text)
        if (cleaned == null || !Note.isValidPage(page)) return null
        val created = createdAt?.takeIf { it > 0 } ?: now
        val updated = maxOf(updatedAt?.takeIf { it > 0 } ?: created, created)
        return Note(0L, bookId, kind, cleaned, page, createdAt = created, updatedAt = updated)
    }

    private fun ImportNote.isAcceptable() = toNote(0L, 0L) != null

    private suspend fun preview(format: LibraryFormat, rows: List<ImportRow>): Try<ImportPreview> {
        val readable = rows.filter { it.title.isNotBlank() }
        val seen = HashSet<String>()
        var duplicates = 0
        var withoutIsbn = 0
        var noteCount = 0
        var loanCount = 0
        val today = clock.todayEpochDay()
        for (row in readable) {
            val isbn13 = row.validIsbn13()
            if (isbn13 == null) {
                withoutIsbn++
                noteCount += row.notes.count { it.isAcceptable() }
                loanCount += row.loans.acceptedAs(0L, today).size
                continue
            }
            when (val existing = books.findByIsbn13(isbn13)) {
                is Try.Failure -> return existing

                is Try.Success -> if (existing.value != null || !seen.add(isbn13)) {
                    duplicates++
                } else {
                    noteCount += row.notes.count { it.isAcceptable() }
                    loanCount += row.loans.acceptedAs(0L, today).size
                }
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
                notes = noteCount,
                loans = loanCount,
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
            series = series?.trim()?.takeIf(String::isNotEmpty),
            seriesIndex = seriesIndex?.takeIf { it.isFinite() && it >= 0 },
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
            format = format,
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
