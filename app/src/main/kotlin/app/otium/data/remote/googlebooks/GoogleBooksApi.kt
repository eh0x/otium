package app.otium.data.remote.googlebooks

import retrofit2.http.GET
import retrofit2.http.Query

internal const val GOOGLE_BOOKS_BASE_URL = "https://www.googleapis.com/books/v1/"
private const val DEFAULT_RESULTS = 10

/**
 * The part of the Google Books API Otium uses. A query is Google's own syntax: `isbn:9780441013593`
 * for a lookup, `intitle:dune inauthor:herbert` for a search.
 */
internal interface GoogleBooksApi {
    @GET("volumes")
    suspend fun search(
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = DEFAULT_RESULTS,
        @Query("printType") printType: String = "books",
    ): VolumesResponse
}
