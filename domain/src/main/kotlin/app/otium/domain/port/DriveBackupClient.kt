package app.otium.domain.port

import app.otium.domain.error.Try
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.RemoteBackup
import java.io.File

/**
 * The user's backups in their Google Drive app data folder, which only this app can see. At most
 * two are kept: the latest and the one before it. Every call fails with `AuthExpired` when the
 * user has not signed in or the access has run out, `Offline` or `Timeout` when Drive cannot be
 * reached, and `InsufficientDriveSpace` when it is full.
 */
interface DriveBackupClient {
    /** The room left in Drive in bytes, or null when Drive does not limit it. */
    suspend fun freeSpace(): Try<Long?>

    /**
     * Stores [file], described by [info], as the latest backup; the one it replaces becomes the
     * previous backup and the one before that is dropped. A failed upload changes nothing.
     */
    suspend fun upload(file: File, info: BackupInfo): Try<Unit>

    /** The backups in Drive, newest first. Empty when none was made. */
    suspend fun list(): Try<List<RemoteBackup>>

    /** Downloads the backup in [slot] to a new temporary file; the caller deletes it. */
    suspend fun download(slot: BackupSlot): Try<File>
}
