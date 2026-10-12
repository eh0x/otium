package app.otium.domain.port

import app.otium.domain.error.Try

/**
 * Reads and writes the library files the owner picks or creates on the device, given their
 * location (a content URI). Nothing else is read or written.
 */
interface LibraryFiles {
    /**
     * The text of the file at [uri]. Fails with `InvalidInput("file")` when it cannot be read or
     * is too large to be a library export, and with `Storage` for other I/O failures.
     */
    suspend fun read(uri: String): Try<String>

    /** Replaces the contents of the file at [uri] with [text]. */
    suspend fun write(uri: String, text: String): Try<Unit>
}
