package app.otium.domain.port

import app.otium.domain.error.Try
import java.io.File

/** Puts the contents of a backup file in place of the library. */
interface LibraryRestorer {
    /**
     * Replaces every book and cover with those in [file], a backup the archiver accepted. The
     * change is all or nothing: on any failure the library is exactly as it was. A backup from an
     * older schema is brought up to date. Fails with `CorruptBackup` when the file is damaged and
     * `Storage` when the device cannot take the change.
     */
    suspend fun restore(file: File): Try<Unit>
}
