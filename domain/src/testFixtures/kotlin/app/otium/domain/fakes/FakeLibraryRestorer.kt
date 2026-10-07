package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BackupInfo
import app.otium.domain.port.LibraryRestorer
import java.io.File

/** Remembers what it was asked to restore; leaves the pretend library alone when it fails. */
class FakeLibraryRestorer : LibraryRestorer {
    /** Set to make [restore] fail. */
    var failure: AppError? = null

    /** The backup the library now matches, or null while it holds its own data. */
    var restored: BackupInfo? = null
        private set

    override suspend fun restore(file: File): Try<Unit> {
        failure?.let { return Try.Failure(it) }
        restored = FakeBackupFiles.read(file) ?: return Try.Failure(AppError.CorruptBackup)
        return Try.Success(Unit)
    }
}
