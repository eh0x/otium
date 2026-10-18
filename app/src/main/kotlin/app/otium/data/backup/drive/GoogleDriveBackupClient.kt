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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Response

private const val ZIP_TYPE = "application/zip"
private const val MAX_TRIES = 3
private const val RETRY_DELAY_MILLIS = 1_000L
private const val BUFFER_BYTES = 64 * 1024
private const val DEFAULT_MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024

/** How a transfer is checked: against the SHA-256 that Drive reports (`sha256Checksum`). */
private const val CHECKSUM_ALGORITHM = "SHA-256"

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
 *
 * The upload address Drive hands back is used only when [trustsUploadAddress] accepts it, so the
 * token is never sent anywhere else.
 */
internal class GoogleDriveBackupClient(
    private val api: DriveApi,
    private val tokens: AccessTokenProvider,
    private val cacheDir: File,
    private val dispatchers: DispatcherProvider,
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS,
    private val maxDownloadBytes: Long = DEFAULT_MAX_DOWNLOAD_BYTES,
    private val trustsUploadAddress: (HttpUrl) -> Boolean = ::isGoogleUploadAddress,
) : DriveBackupClient {

    override suspend fun freeSpace(): Try<Long?> = drive {
        val quota = retrying { auth -> api.about(auth) }.storageQuota
        val limit = quota?.limit?.toLongOrNull()
        val usage = quota?.usage?.toLongOrNull() ?: 0L
        limit?.let { (it - usage).coerceAtLeast(0L) }
    }

    override suspend fun list(): Try<List<RemoteBackup>> = drive { layout().backups() }

    override suspend fun upload(file: File, info: BackupInfo): Try<Unit> = drive {
        val layout = settledLayout()
        val uploaded = send(file, info)
        layout.previous?.let { retrying { auth -> api.deleteFile(it.id, auth) } }
        layout.latest?.let { rename(it, DriveFileNames.PREVIOUS) }
        rename(uploaded, DriveFileNames.LATEST)
    }

    override suspend fun download(slot: BackupSlot): Try<File> = drive {
        val remote = layout().fileFor(slot) ?: throw DriveFailure(AppError.NotFound)
        val target =
            withContext(dispatchers.io) { File.createTempFile("restore", ".zip", cacheDir) }
        var complete = false
        try {
            fetch(remote, target)
            complete = true
            target
        } finally {
            if (!complete) target.delete()
        }
    }

    private suspend fun layout() = DriveLayout(retrying { auth -> api.listFiles(auth) }.files)

    /**
     * The layout after clearing up an earlier upload that stopped before its last step: a finished
     * upload with no latest backup becomes the latest; one next to a latest is dropped, as a new
     * upload is about to replace it.
     */
    private suspend fun settledLayout(): DriveLayout {
        val layout = layout()
        val stray = layout.incoming ?: return layout
        if (layout.latest == null) {
            rename(stray, DriveFileNames.LATEST)
        } else {
            retrying { auth -> api.deleteFile(stray.id, auth) }
        }
        return layout()
    }

    private suspend fun rename(file: DriveFile, name: String) {
        retrying { auth -> api.updateFile(file.id, auth, DriveFileMetadata(name = name)) }
    }

    /** Uploads [file] as the incoming backup and checks Drive stored the same bytes. */
    private suspend fun send(file: File, info: BackupInfo): DriveFile {
        val metadata = DriveFileMetadata(
            name = DriveFileNames.INCOMING,
            parents = listOf(APP_DATA_FOLDER),
            appProperties = info.toProperties(),
        )
        val started = call { auth ->
            api.startUpload(auth, ZIP_TYPE, file.length(), metadata).apply { requireSuccess() }
        }
        val session = uploadAddress(started)
        val content = file.asRequestBody(ZIP_TYPE.toMediaType())
        val sent = call { auth -> api.uploadContent(session.toString(), auth, content) }
        val stored = retrying { auth -> api.getFile(sent.id, auth) }
        val expected = withContext(dispatchers.io) { sha256Of(file) }
        val reported = stored.sha256Checksum
        if (reported == null || !reported.equals(expected, ignoreCase = true)) {
            retrying { auth -> api.deleteFile(sent.id, auth) }
            throw DriveFailure(AppError.Unexpected("Drive stored a different file"))
        }
        return stored
    }

    /** Where to send the bytes, from the `Location` of [started]; only an address we trust. */
    private fun uploadAddress(started: Response<Unit>): HttpUrl {
        val location = started.requireSuccess().headers()["Location"]
            ?: throw DriveFailure(AppError.Unexpected("Drive gave no upload address"))
        return location.toHttpUrlOrNull()?.takeIf(trustsUploadAddress)
            ?: throw DriveFailure(AppError.Unexpected("Drive gave an unexpected upload address"))
    }

    /** Streams [remote] into [target], checking its checksum and size. */
    private suspend fun fetch(remote: DriveFile, target: File) {
        val expected = remote.sha256Checksum ?: throw DriveFailure(AppError.CorruptBackup)
        val body = retrying { auth -> api.download(remote.id, auth) }
        withContext(dispatchers.io) {
            val digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM)
            val withinLimit = body.use { source ->
                target.outputStream().use { out ->
                    copyDigesting(source.byteStream(), out, digest, maxDownloadBytes)
                }
            }
            if (!withinLimit || !digest.hex().equals(expected, ignoreCase = true)) {
                throw DriveFailure(AppError.CorruptBackup)
            }
        }
    }

    /** Runs [block], turning everything it throws into a typed [Try] failure. */
    private suspend fun <T> drive(block: suspend () -> T): Try<T> = try {
        Try.Success(block())
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

    /**
     * Runs [action] with a token, dropping a refused token and trying once more with a fresh one.
     * If the fresh token is refused too, the grant is marked revoked.
     */
    private suspend fun <T> call(action: suspend (auth: String) -> T): T {
        val token = tokens.token()
        return try {
            action("Bearer $token")
        } catch (error: HttpException) {
            if (!error.isRefusedToken()) throw error
            withFreshToken(token, action)
        }
    }

    /** Drops [refused] and runs [action] once more with a fresh token. */
    private suspend fun <T> withFreshToken(
        refused: String,
        action: suspend (auth: String) -> T,
    ): T {
        tokens.discard(refused)
        val fresh = tokens.token()
        return try {
            action("Bearer $fresh")
        } catch (error: HttpException) {
            if (error.isRefusedToken()) {
                tokens.revoked()
                throw DriveFailure(AppError.AuthExpired)
            }
            throw error
        }
    }

    /** Asks again, a little later each time, while Drive answers busy or with a server error. */
    private suspend fun <T> retrying(action: suspend (auth: String) -> T): T {
        var tries = 0
        while (true) {
            try {
                return call(action)
            } catch (error: HttpException) {
                tries++
                if (!error.isTransient() || tries >= MAX_TRIES) throw error
                delay(retryDelayMillis * tries)
            }
        }
    }
}

/** An outcome the caller reports as is, thrown from inside [GoogleDriveBackupClient.drive] to leave early. */
private class DriveFailure(val error: AppError) : RuntimeException(error.toString())

private suspend fun AccessTokenProvider.token(): String = when (val result = accessToken()) {
    is Try.Failure -> throw DriveFailure(result.error)
    is Try.Success -> result.value
}

private fun Response<*>.requireSuccess(): Response<*> =
    if (isSuccessful) this else throw HttpException(this)

private fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM)
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

/**
 * Copies [input] to [output] while feeding [digest], giving up promptly when cancelled or when
 * the stream exceeds [maxBytes].
 */
private fun CoroutineScope.copyDigesting(
    input: InputStream,
    output: OutputStream,
    digest: MessageDigest,
    maxBytes: Long,
): Boolean {
    val buffer = ByteArray(BUFFER_BYTES)
    var total = 0L
    var count = input.read(buffer)
    while (count >= 0) {
        ensureActive()
        total += count
        if (total > maxBytes) return false
        digest.update(buffer, 0, count)
        output.write(buffer, 0, count)
        count = input.read(buffer)
    }
    return true
}
