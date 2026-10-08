package app.otium.data.remote.openlibrary

import app.otium.data.remote.RemoteClients
import app.otium.testing.Fixtures
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class OpenLibraryApiTest {

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private val api: OpenLibraryApi by lazy {
        val client = RemoteClients.httpClient("Otium/test (contact)")
        RemoteClients.retrofit(server.url("/"), client).create(OpenLibraryApi::class.java)
    }

    private fun reply(fixture: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(Fixtures.read(fixture)))
    }

    @Test
    fun `an edition is read by isbn`() = runBlocking {
        reply("openlibrary/edition-dune.json")

        val edition = api.edition("9780441013593")

        assertEquals("/isbn/9780441013593.json", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("Dune", edition.title)
    }

    @Test
    fun `an isbn redirect to the edition record is followed`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "/books/OL7524304M.json"),
        )
        reply("openlibrary/edition-dune.json")

        val edition = api.edition("9780441013593")

        assertEquals("Dune", edition.title)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a work is read by the id from its key`() = runBlocking {
        reply("openlibrary/work-dune.json")

        api.work("OL893414W")

        assertEquals("/works/OL893414W.json", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun `an author is read by the id from its key`() = runBlocking {
        reply("openlibrary/author-dune.json")

        val author = api.author("OL79034A")

        assertEquals("/authors/OL79034A.json", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("Frank Herbert", author.name)
    }

    @Test
    fun `a search sends the text, a limit and only the fields the app uses`() = runBlocking {
        reply("openlibrary/search-dune.json")

        val results = api.search("dune herbert", limit = 5)
        val url = checkNotNull(server.takeRequest().requestUrl)

        assertEquals("/search.json", url.encodedPath)
        assertEquals("dune herbert", url.queryParameter("q"))
        assertEquals("5", url.queryParameter("limit"))
        assertEquals(
            true,
            url.queryParameter("fields").orEmpty().split(',').containsAll(
                listOf("title", "author_name", "cover_i", "isbn"),
            ),
        )
        assertEquals("Dune", results.toCandidates().first().title)
    }

    @Test
    fun `the user agent identifies the app`() = runBlocking {
        reply("openlibrary/search-empty.json")

        api.search("anything")

        assertEquals("Otium/test (contact)", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `an unknown isbn surfaces as an http 404`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not found"))

        val error = assertThrows(HttpException::class.java) {
            runBlocking { api.edition("9789999999990") }
        }

        assertEquals(404, error.code())
    }
}
