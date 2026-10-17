package app.otium.data.backup

import android.content.Context
import app.otium.data.cover.coversDirectory
import app.otium.domain.usecase.BackupLock
import java.io.File

private const val WORK_DIRECTORY = "backup"
private const val DRIVE_RESTORE_PREFIX = "restore"
private const val DRIVE_RESTORE_SUFFIX = ".zip"

/** The folders a backup or restore works in. */
internal data class BackupFiles(
    /** Scratch space for archives being made or unpacked; safe to empty at any time. */
    val work: File,
    /** Where the library's cover pictures live. */
    val covers: File,
) {
    /**
     * Empties [work] and removes leftover Drive restore downloads once no backup or restore holds
     * [lock]. Returns how many entries were deleted.
     */
    suspend fun purgeNow(lock: BackupLock): Int = lock.withLock {
        val scratch = work.listFiles().orEmpty().toList()
        val downloads = work.parentFile?.listFiles().orEmpty().filter {
            it.isFile &&
                it.name.startsWith(DRIVE_RESTORE_PREFIX) &&
                it.name.endsWith(DRIVE_RESTORE_SUFFIX)
        }
        (scratch + downloads).count(File::deleteRecursively)
    }

    companion object {
        fun of(context: Context) =
            BackupFiles(File(context.cacheDir, WORK_DIRECTORY), coversDirectory(context))
    }
}
