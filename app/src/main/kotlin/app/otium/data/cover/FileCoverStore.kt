package app.otium.data.cover

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import app.otium.data.remote.await
import app.otium.data.remote.toAppError
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.CoverStore
import app.otium.domain.port.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val COVERS_DIRECTORY = "covers"
private const val MAX_COVER_BYTES = 5_000_000L
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429

/** Below this a file cannot be a real cover; sites answer a missing cover with a tiny stub. */
private const val MIN_COVER_BYTES = 1_000

/** A real cover is at least this many pixels on both sides; placeholders are a pixel or so. */
private const val MIN_COVER_SIDE = 50

/** Photos from the gallery or camera are scaled so their longer side is at most this. */
private const val MAX_STORED_SIDE = 1_200
private const val JPEG_QUALITY = 90
private const val DEGREES_90 = 90
private const val DEGREES_180 = 180
private const val DEGREES_270 = 270

/**
 * Stores covers as files in the app's private storage. A cover is downloaded once, checked, and
 * written to a temporary file that is renamed into place, so a failed or cancelled download never
 * leaves a half-written cover that a book could point at.
 */
class FileCoverStore internal constructor(
    private val directory: File,
    private val resolver: ContentResolver,
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider,
    private val maxBytes: Long,
) : CoverStore {

    @Inject
    internal constructor(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        dispatchers: DispatcherProvider,
    ) : this(
        File(context.filesDir, COVERS_DIRECTORY),
        context.contentResolver,
        client,
        dispatchers,
        MAX_COVER_BYTES,
    )

    override suspend fun saveFromUrl(url: String): Try<String> = withContext(dispatchers.io) {
        when (val downloaded = download(url)) {
            is Try.Failure -> downloaded

            is Try.Success -> downloaded.value.takeIf(::isUsablePicture)?.let(::store)
                ?: Try.Failure(AppError.NotFound)
        }
    }

    override suspend fun saveFromBytes(bytes: ByteArray): Try<String> =
        withContext(dispatchers.io) {
            if (isUsablePicture(
                    bytes,
                )
            ) {
                store(bytes)
            } else {
                Try.Failure(AppError.InvalidInput("cover"))
            }
        }

    override suspend fun saveFromDevice(uri: String): Try<String> = withContext(dispatchers.io) {
        readScaled(uri.toUri())?.takeIf(::isUsablePicture)?.let(::store)
            ?: Try.Failure(AppError.InvalidInput("cover"))
    }

    override suspend fun delete(path: String): Try<Unit> = withContext(dispatchers.io) {
        try {
            val file = File(path).canonicalFile
            when {
                // Only direct children of the covers directory: no other file, and no `..` tricks.
                file.parentFile != directory.canonicalFile ->
                    Try.Failure(AppError.InvalidInput("path"))

                file.exists() && !file.delete() ->
                    Try.Failure(AppError.Storage("Could not delete the cover"))

                else -> Try.Success(Unit)
            }
        } catch (error: IOException) {
            Try.Failure(AppError.Storage(error.message ?: "Could not delete the cover"))
        }
    }

    /** The picture's bytes, or why there are none. Oversized bodies count as "no cover". */
    private suspend fun download(url: String): Try<ByteArray> {
        val request = try {
            Request.Builder().url(url).build()
        } catch (ignored: IllegalArgumentException) {
            return Try.Failure(AppError.InvalidInput("url"))
        }
        return try {
            client.newCall(request).await().use(::readBody)
        } catch (error: IOException) {
            Try.Failure(error.toAppError())
        }
    }

    private fun readBody(response: Response): Try<ByteArray> {
        val source = response.body.source()
        return when {
            response.code == HTTP_NOT_FOUND -> Try.Failure(AppError.NotFound)

            response.code == HTTP_TOO_MANY_REQUESTS -> Try.Failure(AppError.RateLimited)

            !response.isSuccessful -> Try.Failure(AppError.Unexpected("HTTP ${response.code}"))

            // `request` is true when more than the limit is waiting to be read.
            source.request(maxBytes + 1) -> Try.Failure(AppError.NotFound)

            else -> Try.Success(source.readByteArray())
        }
    }

    /**
     * The picture at [location] as a JPEG no wider or taller than [MAX_STORED_SIDE], upright as the
     * camera's orientation tag says, or null when it cannot be read as a picture.
     */
    @Suppress("SwallowedException") // Unreadable input is reported as InvalidInput by the caller.
    private fun readScaled(location: Uri): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(location)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sampling = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = resolver.openInputStream(location)
            ?.use { BitmapFactory.decodeStream(it, null, sampling) }
        val degrees = resolver.openInputStream(location)?.use(::rotationDegrees) ?: 0
        bitmap?.scaledToFit(MAX_STORED_SIDE)?.rotated(degrees)?.let { picture ->
            ByteArrayOutputStream()
                .also { picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                .toByteArray()
        }
    } catch (error: IOException) {
        null
    } catch (error: SecurityException) {
        null
    }

    /** The largest power of two that keeps the decoded picture at least [MAX_STORED_SIDE]. */
    private fun sampleSize(width: Int, height: Int): Int {
        var size = 1
        while (maxOf(width, height) / (size * 2) >= MAX_STORED_SIDE) size *= 2
        return size
    }

    private fun Bitmap.scaledToFit(maxSide: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return this
        return scale(
            width * maxSide / longest,
            height * maxSide / longest,
            filter = true,
        )
    }

    private fun Bitmap.rotated(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun rotationDegrees(input: InputStream): Int =
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> DEGREES_90
            ExifInterface.ORIENTATION_ROTATE_180 -> DEGREES_180
            ExifInterface.ORIENTATION_ROTATE_270 -> DEGREES_270
            else -> 0
        }

    private fun isUsablePicture(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_COVER_BYTES) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth >= MIN_COVER_SIDE && bounds.outHeight >= MIN_COVER_SIDE
    }

    private fun store(bytes: ByteArray): Try<String> {
        var partial: File? = null
        return try {
            directory.mkdirs()
            val finished = File(directory, "${UUID.randomUUID()}.jpg")
            partial = File.createTempFile("cover", ".part", directory).also { it.writeBytes(bytes) }
            check(partial.renameTo(finished)) { "Could not move the cover into place" }
            partial = null
            Try.Success(finished.absolutePath)
        } catch (error: IOException) {
            Try.Failure(AppError.Storage(error.message ?: "Could not save the cover"))
        } finally {
            partial?.delete()
        }
    }
}
