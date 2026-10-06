package app.otium.data.remote.googlebooks

import app.otium.data.remote.RemoteClients
import app.otium.testing.Fixtures
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class GoogleBooksApiTest {

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun api(key: String = ""): GoogleBooksApi {
        val client = RemoteClients.googleClient(RemoteClients.httpClient("Otium/test"), key)
        return RemoteClients.retrofit(server.url("/books/v1/"), client)
            .create(GoogleBooksApi::class.java)
    }

    private fun reply(code: Int, fixture: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(Fixtures.read(fixture)))
    }

    @Test
    fun `a search asks for books matching the query and parses the volumes`() = runBlocking {
        reply(200, "googlebooks/volumes-dune.json")

        val response = api().search("isbn:9780441013593")
        val request = server.takeRequest()

        assertEquals("/books/v1/volumes", request.requestUrl?.encodedPath)
        assertEquals("isbn:9780441013593", request.requestUrl?.queryParameter("q"))
        assertEquals("books", request.requestUrl?.queryParameter("printType"))
        assertEquals(listOf("Dune"), response.toCandidates().map { it.title })
    }

    @Test
    fun `the number of results can be limited`() = runBlocking {
        reply(200, "googlebooks/volumes-search.json")

        api().search("intitle:dune", maxResults = 20)

        assertEquals("20", server.takeRequest().requestUrl?.queryParameter("maxResults"))
    }

    @Test
    fun `the api key is sent when there is one`() = runBlocking {
        reply(200, "googlebooks/volumes-empty.json")

        api(key = "secret-key").search("isbn:1")

        assertEquals("secret-key", server.takeRequest().requestUrl?.queryParameter("key"))
    }

    @Test
    fun `no key is sent when it is blank`() = runBlocking {
        reply(200, "googlebooks/volumes-empty.json")

        api(key = "").search("isbn:1")

        assertNull(server.takeRequest().requestUrl?.queryParameter("key"))
    }

    @Test
    fun `the user agent is sent`() = runBlocking {
        reply(200, "googlebooks/volumes-empty.json")

        api().search("isbn:1")

        assertEquals("Otium/test", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `an empty result parses to no candidates`() = runBlocking {
        reply(200, "googlebooks/volumes-empty.json")

        assertEquals(emptyList<Any>(), api().search("isbn:0").toCandidates())
    }

    @Test
    fun `a quota error surfaces as an http 429`() {
        reply(429, "googlebooks/error-429.json")

        val error = assertThrows(HttpException::class.java) {
            runBlocking { api().search("isbn:1") }
        }

        assertEquals(429, error.code())
    }
}
