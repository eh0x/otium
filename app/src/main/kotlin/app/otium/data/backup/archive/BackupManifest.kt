package app.otium.data.backup.archive

import app.otium.domain.model.BackupInfo
import kotlinx.serialization.Serializable

/**
 * The `manifest.json` at the root of a backup file. It is read before anything is unpacked, to
 * tell what the backup holds and whether this version of the app can restore it.
 */
@Serializable
internal data class BackupManifest(
    /** Layout of the zip itself. A backup with a higher number than [FORMAT] is not readable. */
    val format: Int,
    val appVersion: String,
    /** Room schema version of `library.db`. */
    val schemaVersion: Int,
    val createdAt: Long,
    val bookCount: Int,
) {
    val isReadable: Boolean get() = format in 1..FORMAT

    fun toInfo(sizeBytes: Long) =
        BackupInfo(createdAt, sizeBytes, bookCount, appVersion, schemaVersion)

    companion object {
        /** The zip layout this version writes and reads. */
        const val FORMAT = 1
    }
}
