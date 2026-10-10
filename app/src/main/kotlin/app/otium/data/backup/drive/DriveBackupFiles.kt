package app.otium.data.backup.drive

import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.RemoteBackup

/**
 * File names of the three backups Drive can hold. An upload first lands as [INCOMING] and is only
 * renamed to [LATEST] once it is complete and the older ones have been moved up, so a failure at
 * any point leaves the earlier backups whole.
 */
internal object DriveFileNames {
    const val LATEST = "otium-backup.zip"
    const val PREVIOUS = "otium-backup.previous.zip"
    const val INCOMING = "otium-backup.incoming.zip"
}

/** What is in the app data folder, sorted into the roles above. */
internal class DriveLayout(files: List<DriveFile>) {
    val latest: DriveFile? = files.firstOrNull { it.name == DriveFileNames.LATEST }
    val previous: DriveFile? = files.firstOrNull { it.name == DriveFileNames.PREVIOUS }
    val incoming: DriveFile? = files.firstOrNull { it.name == DriveFileNames.INCOMING }

    /**
     * The file that serves as the latest backup. A finished upload that was never renamed (the app
     * stopped between the two steps) counts, so the newest data is not lost to bad timing.
     */
    val effectiveLatest: DriveFile? get() = latest ?: incoming

    fun fileFor(slot: BackupSlot): DriveFile? = when (slot) {
        BackupSlot.LATEST -> effectiveLatest
        BackupSlot.PREVIOUS -> previous
    }

    /** The backups the user can pick from, newest first. Files without readable facts are skipped. */
    fun backups(): List<RemoteBackup> = BackupSlot.entries.mapNotNull { slot ->
        fileFor(slot)?.appProperties?.toBackupInfo()?.let { RemoteBackup(slot, it) }
    }
}

private const val CREATED_AT = "createdAt"
private const val SIZE_BYTES = "sizeBytes"
private const val BOOK_COUNT = "bookCount"
private const val APP_VERSION = "appVersion"
private const val SCHEMA_VERSION = "schemaVersion"
private const val KEY_ID = "keyId"

/**
 * Drive keeps custom properties as strings, so the facts about a backup are written as such. An
 * encrypted backup also records which key opens it, so a restore can ask for the passphrase
 * before downloading anything.
 */
internal fun BackupInfo.toProperties(): Map<String, String> = buildMap {
    put(CREATED_AT, createdAt.toString())
    put(SIZE_BYTES, sizeBytes.toString())
    put(BOOK_COUNT, bookCount.toString())
    put(APP_VERSION, appVersion)
    put(SCHEMA_VERSION, schemaVersion.toString())
    keyId?.let { put(KEY_ID, it) }
}

/** The facts in [this], or null when any is missing or not a number (not a file Otium wrote). */
internal fun Map<String, String>.toBackupInfo(): BackupInfo? {
    val createdAt = this[CREATED_AT]?.toLongOrNull()
    val sizeBytes = this[SIZE_BYTES]?.toLongOrNull()
    val bookCount = this[BOOK_COUNT]?.toIntOrNull()
    val appVersion = this[APP_VERSION]
    val schemaVersion = this[SCHEMA_VERSION]?.toIntOrNull()
    val complete = createdAt != null && sizeBytes != null && bookCount != null &&
        appVersion != null && schemaVersion != null
    return if (complete) {
        BackupInfo(createdAt, sizeBytes, bookCount, appVersion, schemaVersion, this[KEY_ID])
    } else {
        null
    }
}
