package app.otium.domain.port

import app.otium.domain.error.Try
import app.otium.domain.model.Book

/** Every book in the library with its labels, read from one consistent view, for an export. */
fun interface LibrarySnapshot {
    suspend fun books(): Try<List<Book>>
}
