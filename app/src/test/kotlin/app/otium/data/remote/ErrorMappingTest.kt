package app.otium.data.remote

import app.otium.domain.error.AppError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorMappingTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `429 means rate limited`() {
        assertEquals(AppError.RateLimited, http(429).toAppError())
    }

    @Test
    fun `404 means not found`() {
        assertEquals(AppError.NotFound, http(404).toAppError())
    }

    @Test
    fun `server errors are unexpected and carry the status`() {
        val error = http(503).toAppError()

        assertTrue(error is AppError.Unexpected)
        assertTrue((error as AppError.Unexpected).message.contains("503"))
    }

    @Test
    fun `an unauthorized or forbidden answer is unexpected, not a rate limit`() {
        assertTrue(http(403).toAppError() is AppError.Unexpected)
        assertTrue(http(401).toAppError() is AppError.Unexpected)
    }

    @Test
    fun `socket and call timeouts mean timeout`() {
        assertEquals(AppError.Timeout, SocketTimeoutException("read timed out").toAppError())
        assertEquals(AppError.Timeout, InterruptedIOException("timeout").toAppError())
    }

    @Test
    fun `no route to the server means offline`() {
        assertEquals(AppError.Offline, UnknownHostException("openlibrary.org").toAppError())
        assertEquals(AppError.Offline, ConnectException("refused").toAppError())
        assertEquals(AppError.Offline, IOException("unexpected end of stream").toAppError())
    }

    @Test
    fun `an unreadable response is unexpected`() {
        assertTrue(SerializationException("bad json").toAppError() is AppError.Unexpected)
    }

    @Test
    fun `anything else is unexpected`() {
        assertTrue(IllegalStateException("boom").toAppError() is AppError.Unexpected)
    }
}
