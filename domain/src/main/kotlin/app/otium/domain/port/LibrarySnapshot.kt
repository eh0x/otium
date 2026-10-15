package app.otium.domain.port

import app.otium.domain.error.Try
import app.otium.domain.model.LibraryEntry

/**
 * Every book in the library with its labels and notes, read from one consistent view, for an
 * export.
 */
fun interface LibrarySnapshot {
    suspend fun entries(): Try<List<LibraryEntry>>
}
