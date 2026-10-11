package app.otium.domain.model

/** Where a backup sits in Drive: the newest one, or the one it replaced. */
enum class BackupSlot { LATEST, PREVIOUS }

/** A backup kept in Drive, described by what was recorded when it was uploaded. */
data class RemoteBackup(val slot: BackupSlot, val info: BackupInfo)

/**
 * A backup downloaded and checked, waiting for the user to confirm replacing the library with it.
 * The downloaded file itself stays inside the restore use case (see `LibraryRestore`).
 */
data class RestorePreview(val slot: BackupSlot, val info: BackupInfo)
