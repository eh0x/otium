package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
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

    override fun write(books: List<Book>, exportedAt: Long): String =
        "{\"books\":${books.size},\"exportedAt\":$exportedAt}"
}

/** The books of a [FakeBookRepository], or [failWith]. */
class FakeLibrarySnapshot(private val repo: FakeBookRepository) : LibrarySnapshot {
    var failWith: AppError? = null

    override suspend fun books(): Try<List<Book>> =
        failWith?.let { Try.Failure(it) } ?: Try.Success(repo.all.sortedBy { it.id })
}
