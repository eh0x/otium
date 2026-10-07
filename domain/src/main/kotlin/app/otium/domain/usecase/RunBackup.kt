package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.error.map
import app.otium.domain.model.BackupInfo
import app.otium.domain.port.BackupArchiver
import app.otium.domain.port.BackupPreferences
import app.otium.domain.port.DriveBackupClient
import app.otium.domain.port.LocalBackup

/**
 * Backs the library up to Drive: archive it, check Drive has room, upload, and remember the
 * result. The library is only ever read, so a failure at any step leaves it untouched, and Drive
 * keeps its earlier backups until the new one is safely stored.
 *
 * An empty library is not backed up. After a reinstall it would replace the very backup the user
 * is about to restore.
 */
class RunBackup(
    private val drive: DriveBackupClient,
    private val archiver: BackupArchiver,
    private val preferences: BackupPreferences,
    private val lock: BackupLock,
) {
    suspend operator fun invoke(): Try<BackupInfo> = lock.withLock {
        when (val made = archiver.create()) {
            is Try.Failure -> made

            is Try.Success -> try {
                upload(made.value)
            } finally {
                made.value.file.delete()
            }
        }
    }

    private suspend fun upload(backup: LocalBackup): Try<BackupInfo> {
        val (file, info) = backup
        if (info.bookCount == 0) return Try.Failure(AppError.NothingToBackUp)
        return checkRoom(info.sizeBytes)
            .flatMap { drive.upload(file, info) }
            .map { info.also { preferences.recordBackup(it) } }
    }

    private suspend fun checkRoom(needed: Long): Try<Unit> = drive.freeSpace().flatMap { free ->
        if (free != null && free < needed) {
            Try.Failure(AppError.InsufficientDriveSpace)
        } else {
            Try.Success(Unit)
        }
    }
}
