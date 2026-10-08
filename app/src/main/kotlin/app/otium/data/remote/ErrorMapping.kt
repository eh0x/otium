package app.otium.data.remote

import app.otium.domain.error.AppError
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Turns whatever a remote call threw into the [AppError] the UI knows how to show. Exceptions
 * stop here; nothing above the data layer sees them.
 */
internal fun Throwable.toAppError(): AppError = when (this) {
    is HttpException -> when (val status = code()) {
        HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited
        HTTP_NOT_FOUND -> AppError.NotFound
        else -> AppError.Unexpected("HTTP $status")
    }

    // Both a socket timeout and OkHttp's whole-call timeout are InterruptedIOExceptions. This must
    // come before IOException, which would call them "offline".
    is InterruptedIOException -> AppError.Timeout

    is IOException -> AppError.Offline

    is SerializationException -> AppError.Unexpected(message ?: "Unreadable response")

    else -> AppError.Unexpected(message ?: javaClass.simpleName)
}
