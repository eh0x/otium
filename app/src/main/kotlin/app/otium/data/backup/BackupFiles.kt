package app.otium.data.backup

import android.content.Context
import app.otium.data.cover.coversDirectory
import java.io.File

private const val WORK_DIRECTORY = "backup"

/** The folders a backup or restore works in. */
internal data class BackupFiles(
    /** Scratch space for archives being made or unpacked; safe to empty at any time. */
    val work: File,
    /** Where the library's cover pictures live. */
    val covers: File,
) {
    companion object {
        fun of(context: Context) =
            BackupFiles(File(context.cacheDir, WORK_DIRECTORY), coversDirectory(context))
    }
}
