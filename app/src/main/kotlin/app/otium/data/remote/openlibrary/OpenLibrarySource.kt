package app.otium.data.remote.openlibrary

import app.otium.domain.model.BookCandidate
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

private const val HTTP_NOT_FOUND = 404
private const val MAX_AUTHORS = 3
private val OPEN_LIBRARY_ID = Regex("^OL[0-9]+[A-Z]$")

/**
 * Open Library as a source of candidates. An edition names its authors and work only by key, so a
 * lookup is one request for the edition plus up to four in parallel for names and description.
 * Those follow-ups are best effort: a candidate without them is still worth having.
 */
internal class OpenLibrarySource @Inject constructor(private val api: OpenLibraryApi) {

    /** The edition for [isbn13], or null when Open Library has none. Other failures are thrown. */
    suspend fun byIsbn(isbn13: String): BookCandidate? = coroutineScope {
        val edition = try {
            api.edition(isbn13)
        } catch (error: HttpException) {
            if (error.code() == HTTP_NOT_FOUND) return@coroutineScope null else throw error
        }
        val work = edition.workKey()?.idOrNull()?.let { id ->
            async { bestEffort { api.work(id) } }
        }
        // Some editions name no authors, so the work is asked as well, and only then.
        val authorKeys = edition.authorKeys().ifEmpty { work?.await()?.authorKeys().orEmpty() }
        val authors = authorKeys.mapNotNull { it.idOrNull() }.take(MAX_AUTHORS).map { id ->
            async { bestEffort { api.author(id).name } }
        }

        edition.toCandidate(authors.awaitAll().filterNotNull(), work?.await())
            .takeIf { it.title.isNotBlank() }
    }

    suspend fun search(query: String, limit: Int): List<BookCandidate> =
        api.search(query, limit).toCandidates()

    /** `/works/OL893414W` to `OL893414W`, or null when the key is not an Open Library id. */
    private fun String.idOrNull(): String? =
        substringAfterLast('/').takeIf(OPEN_LIBRARY_ID::matches)

    private suspend fun <T> bestEffort(call: suspend () -> T): T? = try {
        call()
    } catch (ignored: IOException) {
        null
    } catch (ignored: HttpException) {
        null
    } catch (ignored: SerializationException) {
        null
    }
}
