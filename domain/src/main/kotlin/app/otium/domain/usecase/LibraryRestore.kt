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
import app.otium.domain.port.LibraryRestorer
import app.otium.domain.port.LookupScheduler
import java.io.File

/**
 * Restores the library from a backup in Drive, in two steps so the user can see what they are
 * about to restore. [prepare] finds the backup, downloads it, decrypts it when it is encrypted,
 * and checks it; nothing in the library changes. [confirm] then replaces the library with it.
 * The downloaded file stays inside this class: it is deleted when the restore is confirmed (it
 * succeeded or failed), when it is [cancel]led, or when another backup is prepared.
 *
 * An encrypted backup whose key this device does not hold needs its passphrase. Without one,
 * [prepare] fails with `PassphraseRequired` before anything is downloaded, and the user is asked.
 *
 * A backup written by a newer schema than [appSchemaVersion] is refused, first from what Drive
 * recorded, so it is not even downloaded, and again from the downloaded file itself. Older schemas
 * are fine; the restore brings them up to date.
 *
 * One instance serves one screen, which holds at most one prepared backup at a time.
 */
// The whole restore lives here, so it takes every port a restore touches; bundling them into a
// holder type would only hide the dependencies.
@Suppress("LongParameterList")
class LibraryRestore(
    private val drive: DriveBackupClient,
    private val archiver: BackupArchiver,
    private val restorer: LibraryRestorer,
    private val encryption: BackupEncryption,
    private val lookups: LookupScheduler,
    private val lock: BackupLock,
    private val appSchemaVersion: Int,
) {
    private class Prepared(val info: BackupInfo, val file: File)

    private var prepared: Prepared? = null

    /** Downloads and checks the backup in [slot]; an earlier prepared backup is dropped first. */
    suspend fun prepare(
        slot: BackupSlot = BackupSlot.LATEST,
        passphrase: CharArray? = null,
    ): Try<RestorePreview> {
        cancel()
        return drive.list()
            .flatMap { backups -> backups.inSlot(slot) }
            .flatMap { remote -> checkSchema(remote.info).map { remote.info } }
            .flatMap { info -> checkKey(info, passphrase).map { info } }
            .flatMap { info -> download(slot, info, passphrase) }
    }

    /**
     * Replaces the library with the prepared backup. The restorer changes everything or nothing,
     * so a failure leaves the library as it was. Either way the download is gone afterwards; to
     * try again, prepare the backup again. Fails with `NotFound` when nothing is prepared.
     */
    suspend fun confirm(): Try<Unit> {
        val chosen = prepared ?: return Try.Failure(AppError.NotFound)
        prepared = null
        return try {
            lock.withLock {
                restorer.restore(chosen.file).also { result ->
                    if (result is Try.Success) {
                        // A library restored from an encrypted backup keeps being backed up
                        // encrypted.
                        chosen.info.keyId?.let { encryption.adopt(it) }
                        // Books that were waiting for their details when backed up resume the
                        // lookup.
                        lookups.schedule()
                    }
                }
            }
        } finally {
            chosen.file.delete()
        }
    }

    /** Drops the prepared backup, for when the user decides not to restore. */
    fun cancel() {
        prepared?.file?.delete()
        prepared = null
    }

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
        .flatMap { file -> check(slot, file, remote.keyId) }

    /** The plain backup inside [file]; the encrypted download is deleted either way. */
    private suspend fun decrypt(file: File, passphrase: CharArray?): Try<File> = try {
        encryption.decrypt(file, passphrase)
    } finally {
        file.delete()
    }

    private suspend fun check(slot: BackupSlot, file: File, keyId: String?): Try<RestorePreview> {
        val checked = archiver.inspect(file).flatMap { info -> checkSchema(info).map { info } }
        return when (checked) {
            is Try.Success -> {
                val info = checked.value.copy(keyId = keyId)
                prepared = Prepared(info, file)
                Try.Success(RestorePreview(slot, info))
            }

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
