package app.otium.data.backup.drive

import app.otium.data.remote.toAppError
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.RemoteBackup
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.port.DriveBackupClient
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Response

private const val ZIP_TYPE = "application/zip"
private const val MAX_TRIES = 3
private const val RETRY_DELAY_MILLIS = 1_000L
private const val BUFFER_BYTES = 64 * 1024

/**
 * Keeps the user's backups in their Drive app data folder over the REST API. It holds at most two:
 * the latest and the previous one.
 *
 * An upload never disturbs what is there until it is complete and checked. It lands under a
 * temporary name, its checksum is compared with the local file, and only then are the files
 * renamed: drop the old previous, move the latest up, promote the new one. If the app stops
 * part way, the next call finds a sound layout again (see [DriveLayout.effectiveLatest]).
 *
 * Calls that are safe to repeat are retried a couple of times when Drive is busy or failing.
 * The upload itself is not, as it would send the whole file again; a scheduled backup tries
 * again later instead.
 */
internal class GoogleDriveBackupClient(
    private val api: DriveApi,
    private val tokens: AccessTokenProvider,
    private val cacheDir: File,
    private val dispatchers: DispatcherProvider,
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS,
) : DriveBackupClient {

    override suspend fun freeSpace(): Try<Long?> = drive { auth ->
        val quota = retrying { api.about(auth) }.storageQuota
        val limit = quota?.limit?.toLongOrNull()
        val usage = quota?.usage?.toLongOrNull() ?: 0L
        limit?.let { (it - usage).coerceAtLeast(0L) }
    }

    override suspend fun list(): Try<List<RemoteBackup>> = drive { auth -> layout(auth).backups() }

    override suspend fun upload(file: File, info: BackupInfo): Try<Unit> = drive { auth ->
        val layout = settledLayout(auth)
        val uploaded = send(file, info, auth)
        layout.previous?.let { retrying { api.deleteFile(it.id, auth) } }
        layout.latest?.let { rename(it, DriveFileNames.PREVIOUS, auth) }
        rename(uploaded, DriveFileNames.LATEST, auth)
    }

    override suspend fun download(slot: BackupSlot): Try<File> = drive { auth ->
        val remote = layout(auth).fileFor(slot) ?: throw DriveFailure(AppError.NotFound)
        val target =
            withContext(dispatchers.io) { File.createTempFile("restore", ".zip", cacheDir) }
        var complete = false
        try {
            fetch(remote, target, auth)
            complete = true
            target
        } finally {
            if (!complete) target.delete()
        }
    }

    private suspend fun layout(auth: String) = DriveLayout(retrying { api.listFiles(auth) }.files)

    /**
     * The layout after clearing up an earlier upload that stopped before its last step: a finished
     * upload with no latest backup becomes the latest; one next to a latest is dropped, as a new
     * upload is about to replace it.
     */
    private suspend fun settledLayout(auth: String): DriveLayout {
        val layout = layout(auth)
        val stray = layout.incoming ?: return layout
        if (layout.latest == null) {
            rename(stray, DriveFileNames.LATEST, auth)
        } else {
            retrying { api.deleteFile(stray.id, auth) }
        }
        return layout(auth)
    }

    private suspend fun rename(file: DriveFile, name: String, auth: String) {
        retrying { api.updateFile(file.id, auth, DriveFileMetadata(name = name)) }
    }

    /** Uploads [file] as the incoming backup and checks Drive stored the same bytes. */
    private suspend fun send(file: File, info: BackupInfo, auth: String): DriveFile {
        val metadata = DriveFileMetadata(
            name = DriveFileNames.INCOMING,
            parents = listOf(APP_DATA_FOLDER),
            appProperties = info.toProperties(),
        )
        val session = api.startUpload(auth, ZIP_TYPE, file.length(), metadata)
            .requireSuccess()
            .headers()["Location"]
            ?: throw DriveFailure(AppError.Unexpected("Drive gave no upload address"))
        val sent = api.uploadContent(session, auth, file.asRequestBody(ZIP_TYPE.toMediaType()))
        val stored = retrying { api.getFile(sent.id, auth) }
        val expected = withContext(dispatchers.io) { md5Of(file) }
        if (stored.md5Checksum != null && !stored.md5Checksum.equals(expected, ignoreCase = true)) {
            retrying { api.deleteFile(sent.id, auth) }
            throw DriveFailure(AppError.Unexpected("Drive stored a different file"))
        }
        return stored
    }

    /** Streams [remote] into [target], checking its checksum. */
    private suspend fun fetch(remote: DriveFile, target: File, auth: String) {
        val body = retrying { api.download(remote.id, auth) }
        withContext(dispatchers.io) {
            val digest = MessageDigest.getInstance("MD5")
            body.use { source ->
                target.outputStream().use { out -> copyDigesting(source.byteStream(), out, digest) }
            }
            val expected = remote.md5Checksum
            if (expected != null && !digest.hex().equals(expected, ignoreCase = true)) {
                throw DriveFailure(AppError.CorruptBackup)
            }
        }
    }

    /** Runs [block] with the token, turning everything it throws into a typed failure. */
    private suspend fun <T> drive(block: suspend (auth: String) -> T): Try<T> {
        val token = when (val result = tokens.accessToken()) {
            is Try.Failure -> return result
            is Try.Success -> result.value
        }
        return try {
            Try.Success(block("Bearer $token"))
        } catch (error: CancellationException) {
            throw error
        } catch (failure: DriveFailure) {
            Try.Failure(failure.error)
        } catch (error: HttpException) {
            Try.Failure(error.toDriveError())
        } catch (error: IOException) {
            Try.Failure(error.toAppError())
        } catch (error: SerializationException) {
            Try.Failure(error.toAppError())
        }
    }

    /** Asks again, a little later each time, while Drive answers busy or with a server error. */
    private suspend fun <T> retrying(call: suspend () -> T): T {
        var tries = 0
        while (true) {
            try {
                return call()
            } catch (error: HttpException) {
                tries++
                if (!error.isTransient() || tries >= MAX_TRIES) throw error
                delay(retryDelayMillis * tries)
            }
        }
    }

    /** An outcome the caller reports as is, thrown from inside [drive] to leave early. */
    private class DriveFailure(val error: AppError) : RuntimeException(error.toString())
}

private fun Response<*>.requireSuccess(): Response<*> =
    if (isSuccessful) this else throw HttpException(this)

private fun md5Of(file: File): String {
    val digest = MessageDigest.getInstance("MD5")
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_BYTES)
        var count = input.read(buffer)
        while (count >= 0) {
            digest.update(buffer, 0, count)
            count = input.read(buffer)
        }
    }
    return digest.hex()
}

private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

/** Copies [input] to [output] while feeding [digest], giving up promptly when cancelled. */
private fun CoroutineScope.copyDigesting(
    input: InputStream,
    output: OutputStream,
    digest: MessageDigest,
) {
    val buffer = ByteArray(BUFFER_BYTES)
    var count = input.read(buffer)
    while (count >= 0) {
        ensureActive()
        digest.update(buffer, 0, count)
        output.write(buffer, 0, count)
        count = input.read(buffer)
    }
}
