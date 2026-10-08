package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BookCandidate
import app.otium.domain.repo.MetadataRepository

/** A metadata service that answers what the test tells it to, and remembers what it was asked. */
class FakeMetadataRepository : MetadataRepository {
    var isbnResult: Try<BookCandidate> = Try.Failure(AppError.NotFound)
    var searchResult: Try<List<BookCandidate>> = Try.Success(emptyList())

    /** Answers for particular ISBNs; any other ISBN gets [isbnResult]. */
    val isbnResults = mutableMapOf<String, Try<BookCandidate>>()

    /** Every ISBN passed to [lookupByIsbn], oldest first. */
    val lookedUp = mutableListOf<String>()

    /** Every query passed to [searchByTitleAuthor], oldest first. */
    val searched = mutableListOf<String>()

    override suspend fun lookupByIsbn(isbn13: String): Try<BookCandidate> {
        lookedUp += isbn13
        return isbnResults[isbn13] ?: isbnResult
    }

    override suspend fun searchByTitleAuthor(query: String): Try<List<BookCandidate>> {
        searched += query
        return searchResult
    }
}
