package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.CoverStore

/** Keeps covers in memory. Every URL or URI works unless the test lists it in [missing]. */
class FakeCoverStore : CoverStore {
    /** URLs and URIs that have no usable cover; saving them fails. */
    val missing = mutableSetOf<String>()

    /** Every path saved, oldest first. Deleting is the book repository's job. */
    val stored = mutableListOf<String>()

    private var next = 1

    override suspend fun saveFromUrl(url: String): Try<String> {
        if (url in missing) return Try.Failure(AppError.NotFound)
        return Try.Success(store())
    }

    /** Every URI passed to [saveFromDevice], oldest first. */
    val pickedUris = mutableListOf<String>()

    override suspend fun saveFromDevice(uri: String): Try<String> {
        pickedUris += uri
        if (uri in missing) return Try.Failure(AppError.InvalidInput("cover"))
        return Try.Success(store())
    }

    private fun store(): String = "covers/${next++}.jpg".also { stored += it }
}
