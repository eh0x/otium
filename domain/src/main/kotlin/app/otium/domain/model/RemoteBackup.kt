package app.otium.domain.model

import java.io.File

/** Where a backup sits in Drive: the newest one, or the one it replaced. */
enum class BackupSlot { LATEST, PREVIOUS }

/** A backup kept in Drive, described by what was recorded when it was uploaded. */
data class RemoteBackup(val slot: BackupSlot, val info: BackupInfo)

/**
 * A backup downloaded and checked, waiting for the user to confirm replacing the library with it.
 * [file] stays on the device until the restore succeeds or [discard] is called.
 */
data class RestorePreview(val slot: BackupSlot, val info: BackupInfo, val file: File) {
    /** Drops the downloaded file, for when the user decides not to restore. */
    fun discard() {
        file.delete()
    }
}
