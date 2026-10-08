package app.otium.data.backup

import android.content.Context
import android.database.sqlite.SQLiteException
import app.otium.data.backup.archive.BackupZip
import app.otium.data.backup.archive.Extracted
import app.otium.data.local.OtiumDatabase
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.port.LibraryRestorer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Puts a backup in place of the library without closing or swapping Room. The backup's database is
 * opened on its own (which brings an older schema up to date), its covers are copied into the
 * covers directory, and its rows replace the live ones in one transaction. A failure at any point
 * leaves the library as it was, and the covers copied in before it are removed again. Only after
 * the commit are the covers of the replaced library deleted.
 *
 * The cover paths in a backup are those of the install that made it, so each is rewritten to the
 * same file name in this install's covers directory.
 */
class RoomLibraryRestorer internal constructor(
    private val context: Context,
    private val database: OtiumDatabase,
    private val files: BackupFiles,
    private val dispatchers: DispatcherProvider,
) : LibraryRestorer {

    private val zip = BackupZip()
    private val coversDirectory get() = files.covers

    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        database: OtiumDatabase,
        dispatchers: DispatcherProvider,
    ) : this(
        context,
        database,
        BackupFiles.of(context),
        dispatchers,
    )

    override suspend fun restore(file: File): Try<Unit> = withContext(dispatchers.io) {
        val scratch = File(files.work, "restore-${UUID.randomUUID()}")
        val copiedIn = mutableListOf<File>()
        var committed = false
        try {
            check(scratch.mkdirs()) { "Could not create $scratch" }
            when (val unpacked = zip.extract(file, scratch)) {
                is Try.Failure -> unpacked

                is Try.Success -> {
                    val result = apply(unpacked.value, copiedIn)
                    committed = result is Try.Success
                    result
                }
            }
        } catch (error: IOException) {
            Try.Failure(AppError.Storage(error.message ?: "Could not restore the backup"))
        } catch (error: IllegalStateException) {
            Try.Failure(AppError.Storage(error.message ?: "Could not restore the backup"))
        } finally {
            if (!committed) copiedIn.forEach { it.delete() }
            scratch.deleteRecursively()
        }
    }

    private suspend fun apply(backup: Extracted, copiedIn: MutableList<File>): Try<Unit> {
        val rows = when (val checked = readChecked(backup)) {
            is Try.Failure -> return checked
            is Try.Success -> checked.value
        }
        val before = coversDirectory.listFiles().orEmpty().toSet()
        val covers = backup.covers.associateBy { it.name }
        coversDirectory.mkdirs()
        for (cover in covers.values) copyCover(cover)?.let(copiedIn::add)

        return try {
            database.replaceAll(rows.withCoversIn(coversDirectory, covers.keys))
            // The library is the backup's now; only what it does not use goes.
            val kept = covers.keys.map { File(coversDirectory, it) }.toSet()
            (before - kept).forEach { it.delete() }
            Try.Success(Unit)
        } catch (error: SQLiteException) {
            Try.Failure(AppError.Storage(error.message ?: "Could not restore the library"))
        }
    }

    /**
     * The rows of [backup] once it is known to be readable by this version of the app and to hold
     * the library its manifest describes. Room's default error handler deletes a database it
     * finds damaged and starts an empty one, so a damaged file can read as a library with no
     * books; the manifest counted the books when the backup was made, and a different number
     * means the file is not that library.
     */
    private suspend fun readChecked(backup: Extracted): Try<LibraryRows> {
        val manifest = backup.manifest
        return when {
            !manifest.isReadable -> Try.Failure(AppError.CorruptBackup)

            manifest.schemaVersion > OtiumDatabase.VERSION ->
                Try.Failure(
                    AppError.IncompatibleBackup(manifest.schemaVersion, OtiumDatabase.VERSION),
                )

            else -> {
                val rows = readRows(backup.database)
                if (rows != null && rows.books.size == manifest.bookCount) {
                    Try.Success(rows)
                } else {
                    Try.Failure(AppError.CorruptBackup)
                }
            }
        }
    }

    /** The rows of the backup's database, or null when it cannot be opened as this app's library. */
    @Suppress("SwallowedException") // The user is told the backup is damaged, not why.
    private suspend fun readRows(databaseFile: File): LibraryRows? {
        val backupDatabase = openStandalone(context, databaseFile)
        return try {
            backupDatabase.readAll()
        } catch (error: SQLiteException) {
            null
        } catch (error: IllegalStateException) {
            null
        } finally {
            backupDatabase.close()
        }
    }

    /** Copies [cover] in under its own name; null when the covers directory already holds it. */
    private fun copyCover(cover: File): File? {
        val target = File(coversDirectory, cover.name)
        if (target.exists()) return null
        val partial = File(coversDirectory, "${cover.name}.part")
        try {
            cover.copyTo(partial, overwrite = true)
            check(partial.renameTo(target)) { "Could not move ${cover.name} into place" }
        } finally {
            partial.delete()
        }
        return target
    }
}

/**
 * These rows with each cover path pointing into [directory]. A cover the backup did not carry
 * ([present] lacks its file name) is dropped from the book, which keeps its source link.
 */
private fun LibraryRows.withCoversIn(directory: File, present: Set<String>): LibraryRows = copy(
    books = books.map { book ->
        val name = book.coverPath?.let { File(it).name }
        book.copy(coverPath = name?.takeIf { it in present }?.let { File(directory, it).path })
    },
)
