package app.otium.domain.port

import app.otium.domain.error.Try
import app.otium.domain.model.LibraryEntry
import app.otium.domain.transfer.ImportRow

/**
 * Otium's own library file: every field of every book with its notes and loans, in JSON, with a
 * format version. Covers are kept as the address they came from, not as pictures. The JSON library lives
 * in the app module, so this is a port.
 */
interface LibraryJson {
    /** The books in [text]. Fails with `UnknownFileFormat` when it is not an Otium library file. */
    fun read(text: String): Try<List<ImportRow>>

    fun write(entries: List<LibraryEntry>, exportedAt: Long): String
}
