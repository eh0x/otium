package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.LibraryEntry
import app.otium.domain.port.LibraryFiles
import app.otium.domain.port.LibraryJson
import app.otium.domain.port.LibrarySnapshot
import app.otium.domain.transfer.ImportRow

/** Library files held in memory, by URI. */
class FakeLibraryFiles : LibraryFiles {
    /** The files that can be read, by URI. Written files land here too. */
    val files = mutableMapOf<String, String>()

    /** When set, reads and writes fail with this error. */
    var failWith: AppError? = null

    override suspend fun read(uri: String): Try<String> {
        failWith?.let { return Try.Failure(it) }
        return files[uri]?.let { Try.Success(it) } ?: Try.Failure(AppError.InvalidInput("file"))
    }

    override suspend fun write(uri: String, text: String): Try<Unit> {
        failWith?.let { return Try.Failure(it) }
        files[uri] = text
        return Try.Success(Unit)
    }
}

/**
 * Stands in for the JSON library in the app module: any text starting with `{` reads as [rows],
 * and a written file names how many books it holds.
 */
class FakeLibraryJson(var rows: List<ImportRow> = emptyList()) : LibraryJson {
    override fun read(text: String): Try<List<ImportRow>> = Try.Success(rows)

    /** The entries of the last file written. */
    var written: List<LibraryEntry> = emptyList()
        private set

    override fun write(entries: List<LibraryEntry>, exportedAt: Long): String {
        written = entries
        return "{\"books\":${entries.size},\"exportedAt\":$exportedAt}"
    }
}

/**
 * The books of a [FakeBookRepository] with their notes from [notes], or [failWith]. A book's notes
 * are in reading order, as the real snapshot gives them.
 */
class FakeLibrarySnapshot(
    private val repo: FakeBookRepository,
    private val notes: FakeNoteRepository = FakeNoteRepository(),
    private val loans: FakeLoanRepository = FakeLoanRepository(),
) : LibrarySnapshot {
    var failWith: AppError? = null

    override suspend fun entries(): Try<List<LibraryEntry>> =
        failWith?.let { Try.Failure(it) } ?: Try.Success(
            repo.all.sortedBy { it.id }.map { book ->
                LibraryEntry(
                    book,
                    notes.of(book.id),
                    loans.of(book.id).sortedWith(compareBy({ it.lentOn }, { it.id })),
                )
            },
        )
}
