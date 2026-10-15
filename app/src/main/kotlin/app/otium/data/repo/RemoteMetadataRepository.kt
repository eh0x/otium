package app.otium.data.repo

import app.otium.data.remote.googlebooks.GoogleBooksSource
import app.otium.data.remote.openlibrary.OpenLibrarySource
import app.otium.data.remote.toAppError
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.errorOrNull
import app.otium.domain.error.getOrNull
import app.otium.domain.error.map
import app.otium.domain.model.BookCandidate
import app.otium.domain.repo.MetadataRepository
import app.otium.domain.usecase.MergeMetadata
import app.otium.domain.usecase.withSeriesGuess
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException

private const val DEFAULT_TIMEOUT_MILLIS = 8_000L
private const val DEFAULT_BACKOFF_MILLIS = 500L
private const val SEARCH_RESULTS = 20
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500

/**
 * Looks books up in Google Books and Open Library.
 *
 * An ISBN lookup asks both at once and merges what comes back, Google first. Each service gets
 * [timeoutMillis] and, on a 429 or a 5xx, one more try after [backoffMillis]; when a service still
 * fails, the other one's answer is used. A title search asks Google first and Open Library only
 * when Google has nothing.
 */
class RemoteMetadataRepository internal constructor(
    private val google: GoogleBooksSource,
    private val openLibrary: OpenLibrarySource,
    private val timeoutMillis: Long,
    private val backoffMillis: Long,
) : MetadataRepository {

    @Inject
    internal constructor(google: GoogleBooksSource, openLibrary: OpenLibrarySource) :
        this(google, openLibrary, DEFAULT_TIMEOUT_MILLIS, DEFAULT_BACKOFF_MILLIS)

    override suspend fun lookupByIsbn(isbn13: String): Try<BookCandidate> = coroutineScope {
        val fromGoogle = async { attempt { google.byIsbn(isbn13) } }
        val fromOpenLibrary = async { attempt { openLibrary.byIsbn(isbn13) } }
        merged(fromGoogle.await(), fromOpenLibrary.await(), isbn13)
    }

    override suspend fun searchByTitleAuthor(query: String): Try<List<BookCandidate>> {
        val text = query.trim()
        if (text.isEmpty()) return Try.Success(emptyList())

        val fromGoogle = attempt { google.search(text, SEARCH_RESULTS) }
        // Open Library is asked only when Google has nothing to offer.
        val fromOpenLibrary =
            if (fromGoogle.hasResults()) {
                null
            } else {
                attempt {
                    openLibrary.search(text, SEARCH_RESULTS)
                }
            }
        val results = listOfNotNull(fromGoogle, fromOpenLibrary).firstOrNull { it.hasResults() }
        if (results != null) return results.map { found -> found.map { it.withSeriesGuess() } }

        // Neither found anything. That is an empty list only if both services said so.
        val error =
            mostUseful(listOfNotNull(fromGoogle, fromOpenLibrary).mapNotNull { it.errorOrNull() })
        return error?.let { Try.Failure(it) } ?: Try.Success(emptyList())
    }

    private fun merged(
        google: Try<BookCandidate?>,
        openLibrary: Try<BookCandidate?>,
        isbn13: String,
    ): Try<BookCandidate> {
        val candidate = MergeMetadata(google.getOrNull(), openLibrary.getOrNull())
        if (candidate != null) {
            return Try.Success(candidate.copy(isbn13 = candidate.isbn13 ?: isbn13))
        }

        // No candidate. "Not found" is only true when both services answered; a failure means the
        // book may exist, so the failure is what the caller needs to hear.
        val error = mostUseful(listOfNotNull(google.errorOrNull(), openLibrary.errorOrNull()))
        return Try.Failure(error ?: AppError.NotFound)
    }

    private fun Try<List<BookCandidate>>.hasResults() = getOrNull()?.isNotEmpty() == true

    /** Runs [call] with the per-service timeout and retry, and turns any failure into a [Try]. */
    // A remote call can fail in many unrelated ways; all of them become an AppError, and only
    // cancellation, which must reach the caller, is let through.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attempt(call: suspend () -> T): Try<T> = try {
        Try.Success(withTimeout(timeoutMillis) { retryOnce(call) })
    } catch (ignored: TimeoutCancellationException) {
        Try.Failure(AppError.Timeout)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Try.Failure(error.toAppError())
    }

    /** One more try after a short pause when the service is rate limiting or failing. */
    private suspend fun <T> retryOnce(call: suspend () -> T): T = try {
        call()
    } catch (error: HttpException) {
        if (error.code() == HTTP_TOO_MANY_REQUESTS || error.code() >= HTTP_SERVER_ERROR) {
            delay(backoffMillis)
            call()
        } else {
            throw error
        }
    }

    /** When several errors compete, report the one that tells the user the most. */
    private fun mostUseful(errors: List<AppError>): AppError? = errors.minByOrNull { error ->
        ERROR_PRIORITY.indexOf(error).takeIf { it >= 0 }
            ?: Int.MAX_VALUE
    }
}

/** Most useful first. Anything not listed ranks last. */
private val ERROR_PRIORITY: List<AppError> =
    listOf(AppError.Offline, AppError.Timeout, AppError.RateLimited)
