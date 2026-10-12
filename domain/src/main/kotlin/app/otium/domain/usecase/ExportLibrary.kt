package app.otium.domain.usecase

import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.error.map
import app.otium.domain.port.Clock
import app.otium.domain.port.LibraryFiles
import app.otium.domain.port.LibraryJson
import app.otium.domain.port.LibrarySnapshot
import app.otium.domain.transfer.ExportFormat
import app.otium.domain.transfer.GoodreadsCsv
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Writes the whole library to a file the owner chose, so it can go to another app or be kept as
 * a copy: CSV in Goodreads' columns, which most reading apps import, or Otium's JSON, which keeps
 * every field and can be imported back. Cover pictures are not included.
 */
class ExportLibrary(
    private val snapshot: LibrarySnapshot,
    private val files: LibraryFiles,
    private val json: LibraryJson,
    private val clock: Clock,
    /** Writes the text; a large library would hold up the main thread. */
    private val cpu: CoroutineDispatcher,
) {
    /** Returns how many books the file holds. */
    suspend operator fun invoke(uri: String, format: ExportFormat): Try<Int> =
        snapshot.books().flatMap { books ->
            val text = withContext(cpu) {
                when (format) {
                    ExportFormat.CSV -> GoodreadsCsv.write(books)
                    ExportFormat.JSON -> json.write(books, clock.nowMillis())
                }
            }
            files.write(uri, text).map { books.size }
        }
}
