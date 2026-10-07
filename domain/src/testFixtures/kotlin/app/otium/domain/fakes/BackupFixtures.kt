package app.otium.domain.fakes

import app.otium.domain.model.BackupInfo
import java.io.File

fun backupInfo(
    createdAt: Long = 1_000L,
    sizeBytes: Long = 2_048L,
    bookCount: Int = 3,
    appVersion: String = "0.1.0",
    schemaVersion: Int = 1,
) = BackupInfo(createdAt, sizeBytes, bookCount, appVersion, schemaVersion)

/** Fake backup files hold their [BackupInfo] as text, so every fake can read what another wrote. */
internal object FakeBackupFiles {
    private const val SEPARATOR = "|"
    private const val FIELDS = 5
    const val DAMAGED = "not a backup"

    fun write(info: BackupInfo): File = File.createTempFile("backup", ".zip").apply {
        deleteOnExit()
        writeText(encode(info))
    }

    fun encode(info: BackupInfo): String = with(info) {
        listOf(createdAt, sizeBytes, bookCount, appVersion, schemaVersion).joinToString(SEPARATOR)
    }

    /** The info in [file], or null when it is damaged or gone. */
    fun read(file: File): BackupInfo? {
        val fields = file.takeIf { it.exists() }?.readText()?.split(SEPARATOR).orEmpty()
        if (fields.size != FIELDS) return null
        return runCatching {
            BackupInfo(
                createdAt = fields[0].toLong(),
                sizeBytes = fields[1].toLong(),
                bookCount = fields[2].toInt(),
                appVersion = fields[3],
                schemaVersion = fields[4].toInt(),
            )
        }.getOrNull()
    }
}
