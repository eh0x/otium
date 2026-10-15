package app.otium.data.transfer

import android.content.Context
import androidx.core.net.toUri
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.port.LibraryFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Library files the owner picked or created through the system file picker, read and written
 * through the content resolver. A file larger than [MAX_BYTES] is not read: a library of 5,000
 * books exports to a few megabytes, so anything far beyond that is not an export.
 */
class ContentResolverLibraryFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : LibraryFiles {

    override suspend fun read(uri: String): Try<String> = withContext(dispatchers.io) {
        try {
            context.contentResolver.openInputStream(uri.toUri())?.use(::readLimited)
                ?: Try.Failure(AppError.InvalidInput("file"))
        } catch (ignored: FileNotFoundException) {
            Try.Failure(AppError.InvalidInput("file"))
        } catch (ignored: SecurityException) {
            Try.Failure(AppError.InvalidInput("file"))
        } catch (error: IOException) {
            Try.Failure(AppError.Storage(error.message ?: "read failed"))
        }
    }

    override suspend fun write(uri: String, text: String): Try<Unit> = withContext(dispatchers.io) {
        try {
            // "wt" truncates, so a shorter export does not leave the end of an older one behind.
            context.contentResolver.openOutputStream(uri.toUri(), "wt")
                ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?.let { Try.Success(Unit) }
                ?: Try.Failure(AppError.Storage("the file could not be opened"))
        } catch (error: IOException) {
            Try.Failure(AppError.Storage(error.message ?: "write failed"))
        } catch (error: SecurityException) {
            Try.Failure(AppError.Storage(error.message ?: "write refused"))
        }
    }

    private fun readLimited(stream: InputStream): Try<String> {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            bytes.write(buffer, 0, read)
            if (bytes.size() > MAX_BYTES) return Try.Failure(AppError.InvalidInput("file"))
        }
        return Try.Success(bytes.toString(Charsets.UTF_8.name()))
    }

    private companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
        const val BUFFER_BYTES = 64 * 1024
    }
}
