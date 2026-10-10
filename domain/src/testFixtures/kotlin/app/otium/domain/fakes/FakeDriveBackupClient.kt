package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.RemoteBackup
import app.otium.domain.port.DriveBackupClient
import java.io.File

/** A Drive that keeps the latest and previous backup in memory, and fails when told to. */
class FakeDriveBackupClient : DriveBackupClient {
    private val stored = mutableMapOf<BackupSlot, Pair<BackupInfo, String>>()

    /** Room reported by [freeSpace]; null means unlimited. */
    var free: Long? = null

    /** Set to make the matching call fail, leaving Drive unchanged. */
    var failure: AppError? = null

    var uploads = 0
        private set
    var downloads = 0
        private set

    /** Files [download] has produced, so tests can check they were cleaned up. */
    val downloaded = mutableListOf<File>()

    /** Plants a backup in Drive without going through [upload]. */
    fun put(slot: BackupSlot, info: BackupInfo, content: String = FakeBackupFiles.encode(info)) {
        stored[slot] = info to content
    }

    fun infoIn(slot: BackupSlot): BackupInfo? = stored[slot]?.first

    /** What was uploaded to [slot], as the file's text. */
    fun contentIn(slot: BackupSlot): String? = stored[slot]?.second

    override suspend fun freeSpace(): Try<Long?> =
        failure?.let { Try.Failure(it) } ?: Try.Success(free)

    override suspend fun upload(file: File, info: BackupInfo): Try<Unit> {
        failure?.let { return Try.Failure(it) }
        uploads++
        stored[BackupSlot.LATEST]?.let { stored[BackupSlot.PREVIOUS] = it }
        stored[BackupSlot.LATEST] = info to file.readText()
        return Try.Success(Unit)
    }

    override suspend fun list(): Try<List<RemoteBackup>> {
        failure?.let { return Try.Failure(it) }
        return Try.Success(
            BackupSlot.entries.mapNotNull { slot ->
                stored[slot]?.let { (info, _) -> RemoteBackup(slot, info) }
            },
        )
    }

    override suspend fun download(slot: BackupSlot): Try<File> {
        failure?.let { return Try.Failure(it) }
        val (_, content) = stored[slot] ?: return Try.Failure(AppError.NotFound)
        downloads++
        val file = File.createTempFile("download", ".zip").apply { deleteOnExit() }
        downloaded += file
        file.writeText(content)
        return Try.Success(file)
    }
}
