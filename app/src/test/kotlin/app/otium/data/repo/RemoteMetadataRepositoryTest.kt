package app.otium.data.repo

import app.otium.data.remote.RemoteClients
import app.otium.data.remote.googlebooks.GoogleBooksApi
import app.otium.data.remote.googlebooks.GoogleBooksSource
import app.otium.data.remote.openlibrary.OpenLibraryApi
import app.otium.data.remote.openlibrary.OpenLibrarySource
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.testing.Fixtures
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val ISBN = "9780441013593"
private const val GOOGLE_PATH = "/books/v1/volumes"
private const val EDITION_PATH = "/isbn/$ISBN.json"
private const val WORK_PATH = "/works/OL893414W.json"
private const val AUTHOR_PATH = "/authors/OL79034A.json"
private const val SEARCH_PATH = "/search.json"

class RemoteMetadataRepositoryTest {

    /** Answers by path. Each path replays its responses in order and repeats the last one. */
    private class Backend : Dispatcher() {
        private val queues = mutableMapOf<String, ArrayDeque<MockResponse>>()
        val requests = CopyOnWriteArrayList<RecordedRequest>()

        fun on(path: String, vararg responses: MockResponse) {
            queues[path] = ArrayDeque(responses.toList())
        }

        fun hits(path: String) = requests.count { it.requestUrl?.encodedPath == path }

        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            val queue = queues[request.requestUrl?.encodedPath]
                ?: return MockResponse().setResponseCode(404)
            return synchronized(queue) {
                if (queue.size >
                    1
                ) {
                    queue.removeFirst()
                } else {
                    queue.first()
                }
            }
        }
    }

    private val server = MockWebServer()
    private val backend = Backend()

    @Before
    fun start() {
        server.dispatcher = backend
        server.start()
        // Open Library's follow-up records exist unless a test says otherwise.
        backend.on(WORK_PATH, ok("openlibrary/work-dune.json"))
        backend.on(AUTHOR_PATH, ok("openlibrary/author-dune.json"))
    }

    @After
    fun stop() = server.shutdown()

    private fun repository(timeoutMillis: Long = 5_000, backoffMillis: Long = 1) =
        RemoteClients.httpClient("Otium/test").let { client ->
            RemoteMetadataRepository(
                GoogleBooksSource(
                    RemoteClients.retrofit(server.url("/books/v1/"), client)
                        .create(GoogleBooksApi::class.java),
                ),
                OpenLibrarySource(
                    RemoteClients.retrofit(server.url("/"), client)
                        .create(OpenLibraryApi::class.java),
                ),
                timeoutMillis,
                backoffMillis,
            )
        }

    private fun ok(fixture: String) =
        MockResponse().setResponseCode(200).setBody(Fixtures.read(fixture))

    private fun status(code: Int) = MockResponse().setResponseCode(code).setBody("{}")

    private fun lookup(repository: RemoteMetadataRepository = repository(), isbn: String = ISBN) =
        runBlocking { repository.lookupByIsbn(isbn) }

    private fun search(query: String = "dune herbert") =
        runBlocking { repository().searchByTitleAuthor(query) }

    private fun Try<BookCandidate>.candidate(): BookCandidate =
        checkNotNull((this as? Try.Success)?.value) { "Expected a candidate but got $this" }

    private fun Try<*>.error(): AppError =
        checkNotNull((this as? Try.Failure)?.error) { "Expected a failure but got $this" }

    // --- Lookup by ISBN ---

    @Test
    fun `both services answer and the result is merged with Google first`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-dune.json"))
        backend.on(EDITION_PATH, ok("openlibrary/edition-dune.json"))

        val candidate = lookup().candidate()

        assertEquals(BookSource.GOOGLE, candidate.source)
        assertEquals("Dune", candidate.title)
        assertEquals(704, candidate.totalPages)
        assertEquals("Penguin Publishing Group", candidate.publisher)
        assertEquals(listOf("Frank Herbert"), candidate.authors)
        assertTrue(candidate.coverUrl.orEmpty().startsWith("https://books.google.com"))
    }

    @Test
    fun `a field Google lacks is filled from Open Library`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-no-pages.json"))
        backend.on(EDITION_PATH, ok("openlibrary/edition-dune.json"))

        val candidate = lookup().candidate()

        assertEquals(BookSource.GOOGLE, candidate.source)
        assertEquals(544, candidate.totalPages)
    }

    @Test
    fun `Google alone is enough when Open Library does not know the isbn`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-dune.json"))
        backend.on(EDITION_PATH, status(404))

        assertEquals(BookSource.GOOGLE, lookup().candidate().source)
    }

    @Test
    fun `Open Library alone is enough when Google finds nothing, with names and description`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-empty.json"))
        backend.on(EDITION_PATH, ok("openlibrary/edition-dune.json"))

        val candidate = lookup().candidate()

        assertEquals(BookSource.OPEN_LIBRARY, candidate.source)
        assertEquals(listOf("Frank Herbert"), candidate.authors)
        assertNotNull(candidate.description)
        assertEquals(1, backend.hits(WORK_PATH))
        assertEquals(1, backend.hits(AUTHOR_PATH))
    }

    @Test
    fun `a 429 is retried once and then succeeds`() {
        backend.on(GOOGLE_PATH, status(429), ok("googlebooks/volumes-dune.json"))
        backend.on(EDITION_PATH, status(404))

        assertEquals(BookSource.GOOGLE, lookup().candidate().source)
        assertEquals(2, backend.hits(GOOGLE_PATH))
    }

    @Test
    fun `a 429 that persists is given up after one retry and the other service carries on`() {
        backend.on(GOOGLE_PATH, status(429))
        backend.on(EDITION_PATH, ok("openlibrary/edition-dune.json"))

        assertEquals(BookSource.OPEN_LIBRARY, lookup().candidate().source)
        assertEquals(2, backend.hits(GOOGLE_PATH))
    }

    @Test
    fun `a 5xx on one service does not fail the lookup`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-dune.json"))
        backend.on(EDITION_PATH, status(503))

        assertEquals(BookSource.GOOGLE, lookup().candidate().source)
        assertEquals(2, backend.hits(EDITION_PATH))
    }

    @Test
    fun `a slow service does not hold up the fast one beyond the timeout`() {
        backend.on(
            GOOGLE_PATH,
            ok("googlebooks/volumes-dune.json").setBodyDelay(5, TimeUnit.SECONDS),
        )
        backend.on(EDITION_PATH, ok("openlibrary/edition-dune.json"))
        val started = System.nanoTime()

        val result = lookup(repository(timeoutMillis = 400))
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertEquals(BookSource.OPEN_LIBRARY, result.candidate().source)
        assertTrue("took $elapsedMillis ms", elapsedMillis < 3_000)
    }

    @Test
    fun `when both fail differently the more useful error wins`() {
        backend.on(GOOGLE_PATH, status(429))
        backend.on(
            EDITION_PATH,
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
        )

        assertEquals(AppError.Offline, lookup().error())
    }

    @Test
    fun `rate limiting beats an unexpected server error`() {
        backend.on(GOOGLE_PATH, status(429))
        backend.on(EDITION_PATH, status(503))

        assertEquals(AppError.RateLimited, lookup().error())
    }

    @Test
    fun `when both time out the lookup times out`() {
        backend.on(
            GOOGLE_PATH,
            ok("googlebooks/volumes-dune.json").setBodyDelay(5, TimeUnit.SECONDS),
        )
        backend.on(
            EDITION_PATH,
            ok("openlibrary/edition-dune.json").setBodyDelay(5, TimeUnit.SECONDS),
        )

        assertEquals(AppError.Timeout, lookup(repository(timeoutMillis = 300)).error())
    }

    @Test
    fun `nothing found anywhere is not found`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-empty.json"))
        backend.on(EDITION_PATH, status(404))

        assertEquals(AppError.NotFound, lookup().error())
    }

    @Test
    fun `one service finding nothing while the other fails is an error, not not found`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-empty.json"))
        backend.on(EDITION_PATH, status(503))

        val error = lookup().error()

        assertTrue("got $error", error is AppError.Unexpected)
    }

    @Test
    fun `authors come from the work when the edition names none`() {
        // The real Hobbit edition has no authors at all; only its work knows who wrote it.
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-empty.json"))
        backend.on("/isbn/9780547928227.json", ok("openlibrary/edition-hobbit.json"))
        backend.on("/works/OL27482W.json", ok("openlibrary/work-hobbit.json"))
        backend.on("/authors/OL26320A.json", ok("openlibrary/author-tolkien.json"))

        val candidate = lookup(isbn = "9780547928227").candidate()

        assertEquals("The Hobbit", candidate.title)
        assertEquals(listOf("J.R.R. Tolkien"), candidate.authors)
    }

    @Test
    fun `failed follow-up requests only make the candidate less complete`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-empty.json"))
        backend.on(EDITION_PATH, ok("openlibrary/edition-dune.json"))
        backend.on(WORK_PATH, status(500))
        backend.on(AUTHOR_PATH, status(500))

        val candidate = lookup().candidate()

        assertEquals("Dune", candidate.title)
        assertEquals(emptyList<String>(), candidate.authors)
        assertNull(candidate.description)
    }

    // --- Search by title and author ---

    @Test
    fun `Google results are returned and Open Library is not asked`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-search.json"))

        val titles = (search() as Try.Success).value.map { it.title }

        assertEquals(listOf("Dune", "The Google Story (2018 Updated Edition)"), titles)
        assertEquals(0, backend.hits(SEARCH_PATH))
        val request = backend.requests.first { it.requestUrl?.encodedPath == GOOGLE_PATH }
        assertEquals("dune herbert", request.requestUrl?.queryParameter("q"))
        assertEquals("20", request.requestUrl?.queryParameter("maxResults"))
    }

    @Test
    fun `Open Library is used only when Google returns nothing`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-empty.json"))
        backend.on(SEARCH_PATH, ok("openlibrary/search-dune.json"))

        val results = (search() as Try.Success).value

        assertEquals("Dune", results.first().title)
        assertEquals(BookSource.OPEN_LIBRARY, results.first().source)
        assertEquals(1, backend.hits(SEARCH_PATH))
    }

    @Test
    fun `Open Library is used when Google fails`() {
        backend.on(GOOGLE_PATH, status(429))
        backend.on(SEARCH_PATH, ok("openlibrary/search-dune.json"))

        assertEquals(BookSource.OPEN_LIBRARY, (search() as Try.Success).value.first().source)
    }

    @Test
    fun `no results anywhere is an empty list, not an error`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-empty.json"))
        backend.on(SEARCH_PATH, ok("openlibrary/search-empty.json"))

        assertEquals(Try.Success(emptyList<BookCandidate>()), search())
    }

    @Test
    fun `both searches failing reports the error`() {
        backend.on(GOOGLE_PATH, status(429))
        backend.on(SEARCH_PATH, status(429))

        assertEquals(AppError.RateLimited, search().error())
    }

    @Test
    fun `Google finding nothing while Open Library fails is an error, not an empty list`() {
        backend.on(GOOGLE_PATH, ok("googlebooks/volumes-empty.json"))
        backend.on(SEARCH_PATH, status(503))

        assertTrue(search().error() is AppError.Unexpected)
    }

    @Test
    fun `a blank query asks nobody`() {
        assertEquals(Try.Success(emptyList<BookCandidate>()), search("   "))
        assertEquals(0, backend.requests.size)
    }
}
