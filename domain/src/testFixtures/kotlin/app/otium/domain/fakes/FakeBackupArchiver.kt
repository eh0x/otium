package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BackupInfo
import app.otium.domain.port.BackupArchiver
import app.otium.domain.port.LocalBackup
import java.io.File
import kotlinx.coroutines.delay

/** Archives a pretend library of [info]'s size, and reads back what the fakes wrote. */
class FakeBackupArchiver(var info: BackupInfo = backupInfo()) : BackupArchiver {
    /** Set to make the next [create] fail. */
    var createFailure: AppError? = null

    /** Files [create] has produced, so tests can check they were cleaned up. */
    val created = mutableListOf<File>()

    /** The most [create] calls that were ever running at the same time. */
    var maxConcurrent = 0
        private set
    private var running = 0

    override suspend fun create(): Try<LocalBackup> {
        running++
        maxConcurrent = maxOf(maxConcurrent, running)
        try {
            delay(CREATE_MILLIS)
            createFailure?.let { return Try.Failure(it) }
            val file = FakeBackupFiles.write(info).also(created::add)
            return Try.Success(LocalBackup(file, info))
        } finally {
            running--
        }
    }

    override suspend fun inspect(file: File): Try<BackupInfo> =
        FakeBackupFiles.read(file)?.let { Try.Success(it) } ?: Try.Failure(AppError.CorruptBackup)

    private companion object {
        const val CREATE_MILLIS = 10L
    }
}
