package app.otium.domain.port

import app.otium.domain.error.Try
import app.otium.domain.model.BackupInfo
import java.io.File

/** A backup file on the device, together with what it holds. */
data class LocalBackup(val file: File, val info: BackupInfo)

/** Packs the library into one backup file and reads such a file back. */
interface BackupArchiver {
    /**
     * Writes the library, its books and covers, to a new temporary file; the caller deletes it.
     * The library keeps working meanwhile and is never changed. Fails with `Storage` when the file
     * cannot be written.
     */
    suspend fun create(): Try<LocalBackup>

    /**
     * Describes the backup in [file] without restoring it. Fails with `CorruptBackup` when it is
     * not a backup this app wrote or is damaged.
     */
    suspend fun inspect(file: File): Try<BackupInfo>
}
