package app.otium.data.backup.archive

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val BUFFER_BYTES = 64 * 1024
private const val DEFAULT_MAX_ENTRIES = 50_000
private const val DEFAULT_MAX_UNPACKED_BYTES = 2L * 1024 * 1024 * 1024

/** A name safe to use as a file name inside the backup: no path separators, no leading dot. */
private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

/** What [BackupZip.extract] unpacked. */
internal class Extracted(val manifest: BackupManifest, val database: File, val covers: List<File>)

/**
 * Reads and writes the backup file, a zip holding `manifest.json`, `library.db` and the covers as
 * `covers/<name>`. A backup comes from the user's Drive, so it is treated as untrusted input: only
 * these entry names are accepted (which rules out paths that climb out of the target folder), and
 * unpacking stops at [maxEntries] entries or [maxUnpackedBytes] bytes, whatever the zip claims.
 */
internal class BackupZip(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxUnpackedBytes: Long = DEFAULT_MAX_UNPACKED_BYTES,
) {
    /**
     * Writes [target]. Covers are stored as they are, as pictures do not compress further. If
     * writing fails, [target] is removed and the exception is thrown for the caller to report.
     */
    @Throws(IOException::class)
    fun write(target: File, manifest: BackupManifest, database: File, covers: List<File>) {
        try {
            ZipOutputStream(BufferedOutputStream(target.outputStream())).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                zip.addFile(DATABASE, database, Deflater.DEFAULT_COMPRESSION)
                covers.filter { SAFE_NAME.matches(it.name) }
                    .forEach {
                        zip.addFile("$COVERS_DIRECTORY${it.name}", it, Deflater.NO_COMPRESSION)
                    }
            }
        } catch (error: IOException) {
            target.delete()
            throw error
        }
    }

    /** The manifest of [zip], without unpacking anything else. */
    fun readManifest(zip: File): Try<BackupManifest> = unreadable {
        ZipFile(zip).use { file ->
            val entry =
                file.getEntry(MANIFEST) ?: return@unreadable Try.Failure(AppError.CorruptBackup)
            Try.Success(json.decodeFromString(BackupManifest.serializer(), file.text(entry)))
        }
    }

    /**
     * Unpacks [zip] into [into]: `library.db` and `covers/`. On any failure everything unpacked
     * so far is removed again.
     */
    fun extract(zip: File, into: File): Try<Extracted> {
        val result = unreadable { ZipFile(zip).use { file -> unpack(file, into) } }
        if (result is Try.Failure) into.listFiles()?.forEach { it.deleteRecursively() }
        return result
    }

    private fun unpack(file: ZipFile, into: File): Try<Extracted> {
        val entries = file.entries().toList().filterNot { it.isDirectory }
        val names = entries.map { it.name }
        if (entries.size > maxEntries || names.size != names.toSet().size) {
            return Try.Failure(AppError.CorruptBackup)
        }
        var manifest: BackupManifest? = null
        var database: File? = null
        val covers = mutableListOf<File>()
        val budget = Budget(maxUnpackedBytes)
        for (entry in entries) {
            if (entry.name == MANIFEST) {
                manifest = json.decodeFromString(BackupManifest.serializer(), file.text(entry))
                continue
            }
            val target = targetOf(entry.name, into)
            if (target == null || !file.getInputStream(entry).use { it.copyTo(target, budget) }) {
                return Try.Failure(AppError.CorruptBackup)
            }
            if (entry.name == DATABASE) database = target else covers += target
        }
        return if (manifest != null && database != null) {
            Try.Success(Extracted(manifest, database, covers))
        } else {
            Try.Failure(AppError.CorruptBackup)
        }
    }

    /** Where an entry goes, or null when its name is not one a backup contains. */
    private fun targetOf(name: String, into: File): File? {
        val file = when {
            name == DATABASE -> File(into, DATABASE)

            name.startsWith(COVERS_DIRECTORY) &&
                SAFE_NAME.matches(name.removePrefix(COVERS_DIRECTORY)) ->
                File(into, name)

            else -> return null
        }
        // Defence in depth: the name check already rules out `..`, links and absolute paths.
        return file.takeIf { it.canonicalPath.startsWith(into.canonicalPath + File.separator) }
    }

    /**
     * Runs [block], reporting a damaged zip or manifest as `CorruptBackup` and disk trouble as
     * `Storage`.
     */
    @Suppress("SwallowedException") // The user is shown the outcome, never the zip's details.
    private inline fun <T> unreadable(block: () -> Try<T>): Try<T> = try {
        block()
    } catch (error: ZipException) {
        Try.Failure(AppError.CorruptBackup)
    } catch (error: SerializationException) {
        Try.Failure(AppError.CorruptBackup)
    } catch (error: IOException) {
        Try.Failure(AppError.Storage(error.message ?: "Could not read the backup"))
    }

    private fun ZipFile.text(entry: ZipEntry): String =
        getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun ZipOutputStream.addFile(name: String, source: File, level: Int) {
        setLevel(level)
        putNextEntry(ZipEntry(name))
        source.inputStream().use { it.copyTo(this, BUFFER_BYTES) }
        closeEntry()
    }

    /** Copies into [target] while [budget] lasts; false when the stream held more than that. */
    private fun InputStream.copyTo(target: File, budget: Budget): Boolean {
        target.parentFile?.mkdirs()
        val buffer = ByteArray(BUFFER_BYTES)
        var within = true
        target.outputStream().use { out ->
            var count = read(buffer)
            while (count >= 0 && within) {
                budget.remaining -= count
                within = budget.remaining >= 0
                if (within) out.write(buffer, 0, count)
                count = read(buffer)
            }
        }
        return within
    }

    /** How many more bytes may be unpacked. */
    private class Budget(var remaining: Long)

    companion object {
        const val MANIFEST = "manifest.json"
        const val DATABASE = "library.db"
        const val COVERS_DIRECTORY = "covers/"

        private val json = Json { ignoreUnknownKeys = true }
    }
}
