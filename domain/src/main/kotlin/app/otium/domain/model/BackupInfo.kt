package app.otium.domain.model

/** What a backup holds, as recorded when it was made. */
data class BackupInfo(
    val createdAt: Long,
    val sizeBytes: Long,
    val bookCount: Int,
    val appVersion: String,
    /** The library schema the backup was written with; an older app cannot read a newer one. */
    val schemaVersion: Int,
)
