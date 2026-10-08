package app.otium.domain.repo

import app.otium.domain.error.Try
import app.otium.domain.model.BookCandidate

/**
 * Where book details come from when the user does not type them in. The interface lives here
 * because the add flow depends on it; the implementation talks to remote services in the app
 * module.
 */
interface MetadataRepository {
    /**
     * One merged candidate for [isbn13]. Fails with `NotFound` only when every service answered
     * and none knows the book; when a service could not answer, its error is reported instead,
     * because the book may exist.
     */
    suspend fun lookupByIsbn(isbn13: String): Try<BookCandidate>

    /**
     * Candidates for a free-text title and author query, best first. No match is an empty list,
     * not an error. A blank query returns an empty list without asking anyone.
     */
    suspend fun searchByTitleAuthor(query: String): Try<List<BookCandidate>>
}
