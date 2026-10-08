package app.otium.domain.usecase

import app.otium.domain.error.Try
import app.otium.domain.model.RemoteBackup
import app.otium.domain.port.DriveBackupClient

/** Lists the backups in Drive, newest first, so the user can see what a restore would use. */
class FindBackups(private val drive: DriveBackupClient) {
    suspend operator fun invoke(): Try<List<RemoteBackup>> = drive.list()
}
