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
import app.otium.domain.port.BackupEncryption
import app.otium.domain.port.DriveBackupClient
import java.io.File

/**
 * The first half of a restore: finds the backup in Drive, downloads it, decrypts it when it is
 * encrypted, and checks it. Nothing in the library changes. The result tells the user what they
 * are about to restore, and [RestoreBackup] applies it once they confirm.
 *
 * An encrypted backup whose key this device does not hold needs its passphrase. Without one the
 * call fails with `PassphraseRequired` before anything is downloaded, and the user is asked.
 *
 * A backup written by a newer schema than [appSchemaVersion] is refused, first from what Drive
 * recorded, so it is not even downloaded, and again from the downloaded file itself. Older
 * schemas are fine; the restore brings them up to date.
 */
class PrepareRestore(
    private val drive: DriveBackupClient,
    private val archiver: BackupArchiver,
    private val encryption: BackupEncryption,
    private val appSchemaVersion: Int,
) {
    suspend operator fun invoke(
        slot: BackupSlot = BackupSlot.LATEST,
        passphrase: CharArray? = null,
    ): Try<RestorePreview> = drive.list()
        .flatMap { backups -> backups.inSlot(slot) }
        .flatMap { remote -> checkSchema(remote.info).map { remote.info } }
        .flatMap { info -> checkKey(info, passphrase).map { info } }
        .flatMap { info -> download(slot, info, passphrase) }

    private suspend fun checkKey(info: BackupInfo, passphrase: CharArray?): Try<Unit> {
        val keyId = info.keyId
        return if (keyId != null && passphrase == null && !encryption.canOpen(keyId)) {
            Try.Failure(AppError.PassphraseRequired)
        } else {
            Try.Success(Unit)
        }
    }

    private suspend fun download(
        slot: BackupSlot,
        remote: BackupInfo,
        passphrase: CharArray?,
    ): Try<RestorePreview> = drive.download(slot)
        .flatMap { file ->
            if (remote.isEncrypted) decrypt(file, passphrase) else Try.Success(file)
        }
        .flatMap { file -> preview(slot, file, remote.keyId) }

    /** The plain backup inside [file]; the encrypted download is deleted either way. */
    private suspend fun decrypt(file: File, passphrase: CharArray?): Try<File> = try {
        encryption.decrypt(file, passphrase)
    } finally {
        file.delete()
    }

    private suspend fun preview(slot: BackupSlot, file: File, keyId: String?): Try<RestorePreview> {
        val checked = archiver.inspect(file).flatMap { info -> checkSchema(info).map { info } }
        return when (checked) {
            is Try.Success ->
                Try.Success(RestorePreview(slot, checked.value.copy(keyId = keyId), file))

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
