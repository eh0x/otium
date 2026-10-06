package app.otium.domain.port

import app.otium.domain.error.Try

/**
 * Keeps book covers on the device. A cover is saved once, when the user adds a book, and the
 * returned path is what the book stores; nothing is fetched again to show it. Only covers of
 * books in the user's own library are ever stored.
 */
interface CoverStore {
    /**
     * Downloads the picture at [url]. Fails with `NotFound` when there is no usable cover there:
     * a missing file, a blank placeholder, or something that is not a picture.
     * Returns the path of the stored file.
     */
    suspend fun saveFromUrl(url: String): Try<String>

    /** Stores a picture the user chose. Fails with `InvalidInput("cover")` if it is not usable. */
    suspend fun saveFromBytes(bytes: ByteArray): Try<String>

    /** Deletes a stored cover. Succeeds when it is already gone; refuses any other file. */
    suspend fun delete(path: String): Try<Unit>
}
