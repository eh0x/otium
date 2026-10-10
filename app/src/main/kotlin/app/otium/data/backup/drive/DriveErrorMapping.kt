package app.otium.data.backup.drive

import app.otium.data.remote.RemoteJson
import app.otium.data.remote.toAppError
import app.otium.domain.error.AppError
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500
private const val HTTP_GATEWAY_TIMEOUT = 504

/** Drive's 403 reasons that mean "too many requests", not "not allowed". */
private val RATE_LIMIT_REASONS = setOf(
    "rateLimitExceeded",
    "userRateLimitExceeded",
    "dailyLimitExceeded",
    "quotaExceeded",
)

/**
 * Turns what a Drive call threw into an [AppError]. Drive uses 403 for several different things,
 * so its reason decides: a full Drive, a busy service, or access the user has to grant again.
 * A busy or failing service is `RateLimited`, which scheduled backups retry later. Anything that
 * is not an HTTP error is handled like the other remote calls.
 */
internal fun Throwable.toDriveError(): AppError = when (this) {
    is HttpException -> when (val status = code()) {
        HTTP_UNAUTHORIZED -> AppError.AuthExpired

        HTTP_FORBIDDEN -> when (reason()) {
            "storageQuotaExceeded" -> AppError.InsufficientDriveSpace
            in RATE_LIMIT_REASONS -> AppError.RateLimited
            else -> AppError.AuthExpired
        }

        HTTP_NOT_FOUND -> AppError.NotFound

        HTTP_REQUEST_TIMEOUT -> AppError.Timeout

        HTTP_TOO_MANY_REQUESTS, in HTTP_SERVER_ERROR..HTTP_GATEWAY_TIMEOUT -> AppError.RateLimited

        else -> AppError.Unexpected("HTTP $status")
    }

    else -> toAppError()
}

/** True when Drive refused the token itself, as opposed to refusing what it was asked to do. */
internal fun HttpException.isRefusedToken(): Boolean = code() == HTTP_UNAUTHORIZED

/** True for the answers worth asking again: too many requests, or the service failing. */
internal fun HttpException.isTransient(): Boolean =
    code() == HTTP_TOO_MANY_REQUESTS || code() in HTTP_SERVER_ERROR..HTTP_GATEWAY_TIMEOUT

/** The first reason Drive gave in its error body, or null when there is none to read. */
@Suppress("SwallowedException") // An unreadable body only means the status alone decides.
private fun HttpException.reason(): String? = try {
    response()?.errorBody()?.string()
        ?.let { RemoteJson.decodeFromString<DriveErrorEnvelope>(it) }
        ?.error?.errors?.firstOrNull()?.reason
} catch (error: SerializationException) {
    null
} catch (error: IOException) {
    null
}
