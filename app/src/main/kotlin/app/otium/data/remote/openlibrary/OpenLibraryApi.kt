package app.otium.data.remote.openlibrary

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal const val OPEN_LIBRARY_BASE_URL = "https://openlibrary.org/"
private const val DEFAULT_RESULTS = 10

/** Only what the app reads, so search responses stay small. */
private const val SEARCH_FIELDS =
    "key,title,author_name,first_publish_year,isbn,cover_i," +
        "number_of_pages_median,publisher,language,subject"

/**
 * The part of the Open Library API Otium uses. `{id}` is the last part of a record key, so
 * `/works/OL893414W` is asked for as `OL893414W`.
 */
internal interface OpenLibraryApi {
    /** Open Library answers with a redirect to the edition record, which OkHttp follows. */
    @GET("isbn/{isbn}.json")
    suspend fun edition(@Path("isbn") isbn: String): EditionDto

    @GET("works/{id}.json")
    suspend fun work(@Path("id") id: String): WorkDto

    @GET("authors/{id}.json")
    suspend fun author(@Path("id") id: String): AuthorDto

    @GET("search.json")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = DEFAULT_RESULTS,
        @Query("fields") fields: String = SEARCH_FIELDS,
    ): SearchResponse
}
