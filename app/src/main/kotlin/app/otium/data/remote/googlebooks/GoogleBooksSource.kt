package app.otium.data.remote.googlebooks

import app.otium.domain.model.BookCandidate
import javax.inject.Inject

/**
 * Google Books as a source of candidates. Failures are thrown, not caught: the repository decides
 * what a failure means for the answer as a whole.
 */
internal class GoogleBooksSource @Inject constructor(private val api: GoogleBooksApi) {

    /** The volume for [isbn13], or null when Google has none. */
    suspend fun byIsbn(isbn13: String): BookCandidate? {
        val candidates = api.search("isbn:$isbn13").toCandidates()
        return candidates.firstOrNull { it.isbn13 == isbn13 } ?: candidates.firstOrNull()
    }

    suspend fun search(query: String, limit: Int): List<BookCandidate> =
        api.search(query, maxResults = limit).toCandidates()
}
