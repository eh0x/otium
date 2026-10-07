package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.error.map
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.RemoteBackup
import app.otium.domain.model.RestorePreview
import app.otium.domain.port.BackupArchiver
import app.otium.domain.port.DriveBackupClient

/**
 * The first half of a restore: finds the backup in Drive, downloads it and checks it. Nothing in
 * the library changes. The result tells the user what they are about to restore, and [RestoreBackup]
 * applies it once they confirm.
 *
 * A backup written by a newer schema than [appSchemaVersion] is refused, first from what Drive
 * recorded, so it is not even downloaded, and again from the downloaded file itself. Older
 * schemas are fine; the restore brings them up to date.
 */
class PrepareRestore(
    private val drive: DriveBackupClient,
    private val archiver: BackupArchiver,
    private val appSchemaVersion: Int,
) {
    suspend operator fun invoke(slot: BackupSlot = BackupSlot.LATEST): Try<RestorePreview> =
        drive.list()
            .flatMap { backups -> backups.inSlot(slot) }
            .flatMap { remote -> checkSchema(remote.info) }
            .flatMap { download(slot) }

    private suspend fun download(slot: BackupSlot): Try<RestorePreview> =
        drive.download(slot).flatMap { file ->
            val checked = archiver.inspect(file).flatMap { info ->
                checkSchema(info).map { info }
            }
            when (checked) {
                is Try.Success -> Try.Success(RestorePreview(slot, checked.value, file))

                is Try.Failure -> {
                    file.delete()
                    checked
                }
            }
        }

    private fun checkSchema(info: BackupInfo): Try<Unit> =
        if (info.schemaVersion > appSchemaVersion) {
            Try.Failure(AppError.IncompatibleBackup(info.schemaVersion, appSchemaVersion))
        } else {
            Try.Success(Unit)
        }
}

private fun List<RemoteBackup>.inSlot(slot: BackupSlot): Try<RemoteBackup> =
    firstOrNull { it.slot == slot }?.let { Try.Success(it) } ?: Try.Failure(AppError.NotFound)
