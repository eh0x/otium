package app.otium.data.backup

import android.content.Context
import android.database.sqlite.SQLiteException
import app.otium.BuildConfig
import app.otium.data.backup.archive.BackupManifest
import app.otium.data.backup.archive.BackupZip
import app.otium.data.cover.CoverFiles
import app.otium.data.local.OtiumDatabase
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.BackupInfo
import app.otium.domain.port.BackupArchiver
import app.otium.domain.port.Clock
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.port.LocalBackup
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Packs the library into a backup zip. The books are copied from one consistent view of the live
 * database into a fresh stand-alone database file, which goes into the zip with the cover pictures
 * and a manifest. Copying through Room, rather than the file or `VACUUM INTO`, works on every
 * Android version Otium supports and never blocks the library: it only reads.
 */
class ZipBackupArchiver internal constructor(
    private val context: Context,
    private val database: OtiumDatabase,
    private val files: BackupFiles,
    private val clock: Clock,
    private val appVersion: String,
    private val dispatchers: DispatcherProvider,
) : BackupArchiver {

    private val zip = BackupZip()

    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        database: OtiumDatabase,
        clock: Clock,
        dispatchers: DispatcherProvider,
    ) : this(
        context,
        database,
        BackupFiles.of(context),
        clock,
        BuildConfig.VERSION_NAME,
        dispatchers,
    )

    override suspend fun create(): Try<LocalBackup> = withContext(dispatchers.io) {
        val scratch = File(files.work, UUID.randomUUID().toString())
        var target: File? = null
        try {
            check(scratch.mkdirs()) { "Could not create $scratch" }
            val rows = database.readAll()
            val snapshot = File(scratch, BackupZip.DATABASE)
            snapshotInto(snapshot, rows)
            target = File.createTempFile("otium-backup", ".zip", files.work)
            val manifest = BackupManifest(
                format = BackupManifest.FORMAT,
                appVersion = appVersion,
                schemaVersion = OtiumDatabase.VERSION,
                createdAt = clock.nowMillis(),
                bookCount = rows.books.size,
            )
            zip.write(target, manifest, snapshot, coverFiles(rows))
            val info = manifest.toInfo(target.length())
            Try.Success(LocalBackup(target, info)).also { target = null }
        } catch (error: IOException) {
            Try.Failure(AppError.Storage(error.message ?: "Could not write the backup"))
        } catch (error: SQLiteException) {
            Try.Failure(AppError.Storage(error.message ?: "Could not read the library"))
        } catch (error: IllegalStateException) {
            Try.Failure(AppError.Storage(error.message ?: "Could not write the backup"))
        } finally {
            target?.delete()
            scratch.deleteRecursively()
        }
    }

    override suspend fun inspect(file: File): Try<BackupInfo> = withContext(dispatchers.io) {
        zip.readManifest(file).flatMap { manifest ->
            if (manifest.isReadable) {
                Try.Success(manifest.toInfo(file.length()))
            } else {
                Try.Failure(AppError.CorruptBackup)
            }
        }
    }

    private suspend fun snapshotInto(file: File, rows: LibraryRows) {
        val copy = openStandalone(context, file)
        try {
            copy.replaceAll(rows)
        } finally {
            copy.close()
        }
    }

    /** The cover pictures the books name that exist in the covers directory, each once. */
    private fun coverFiles(rows: LibraryRows): List<File> {
        val covers = CoverFiles(files.covers)
        val directory = files.covers.canonicalFile
        return rows.books.mapNotNull { covers.pathOf(it.coverPath) }
            .map(::File)
            .filter { it.isFile && it.canonicalFile.parentFile == directory }
            .distinct()
    }
}
