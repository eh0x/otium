package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.error.map
import app.otium.domain.port.LibraryFiles
import app.otium.domain.port.LibraryJson
import app.otium.domain.transfer.CsvTable
import app.otium.domain.transfer.GoodreadsCsv
import app.otium.domain.transfer.ImportRow
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.transfer.StoryGraphCsv
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The books in a library file, and the app that wrote it. */
data class LibraryFile(val format: LibraryFormat, val rows: List<ImportRow>)

/**
 * Reads a library file the owner picked and works out which app wrote it: Otium's JSON names its
 * format, and the CSV exports are told apart by their header row. Fails with `UnknownFileFormat`
 * for anything else.
 */
class ReadLibraryFile(
    private val files: LibraryFiles,
    private val json: LibraryJson,
    /** Parses the file; a large one would hold up the main thread. */
    private val cpu: CoroutineDispatcher,
) {
    suspend operator fun invoke(uri: String): Try<LibraryFile> =
        files.read(uri).flatMap { text -> withContext(cpu) { parse(text) } }

    private fun parse(text: String): Try<LibraryFile> {
        if (text.trimStart().startsWith("{")) {
            return json.read(text).map { LibraryFile(LibraryFormat.OTIUM, it) }
        }
        val table = CsvTable.of(text)
        return when {
            table == null -> Try.Failure(AppError.UnknownFileFormat)

            GoodreadsCsv.matches(table) ->
                Try.Success(LibraryFile(LibraryFormat.GOODREADS, GoodreadsCsv.read(table)))

            StoryGraphCsv.matches(table) ->
                Try.Success(LibraryFile(LibraryFormat.STORYGRAPH, StoryGraphCsv.read(table)))

            else -> Try.Failure(AppError.UnknownFileFormat)
        }
    }
}
