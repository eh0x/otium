package app.otium.domain.model

/** What a backup holds, as recorded when it was made. */
data class BackupInfo(
    val createdAt: Long,
    val sizeBytes: Long,
    val bookCount: Int,
    val appVersion: String,
    /** The library schema the backup was written with; an older app cannot read a newer one. */
    val schemaVersion: Int,
    /**
     * Names the passphrase key the backup is encrypted with, without revealing it; null for a
     * backup that is not encrypted.
     */
    val keyId: String? = null,
) {
    val isEncrypted: Boolean get() = keyId != null
}
