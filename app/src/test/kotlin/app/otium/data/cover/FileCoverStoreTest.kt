package app.otium.data.cover

import app.otium.data.remote.RemoteClients
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.DispatcherProvider
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileCoverStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private object RealDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    private val coversDir get() = File(temp.root, "covers")

    private fun store(maxBytes: Long = 5_000_000L) = FileCoverStore(
        directory = coversDir,
        client = RemoteClients.httpClient("Otium/test"),
        dispatchers = RealDispatchers,
        maxBytes = maxBytes,
    )

    /** A cover-sized picture. Noise keeps the file well above the "tiny" threshold. */
    private fun picture(width: Int = 120, height: Int = 180): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = Random(42)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, random.nextInt())
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun serve(bytes: ByteArray, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(Buffer().write(bytes)))
    }

    private fun savedFiles() = coversDir.listFiles()?.toList().orEmpty()

    private fun Try<String>.path(): String =
        checkNotNull((this as? Try.Success)?.value) { "Expected a path but got $this" }

    private fun Try<*>.error(): AppError =
        checkNotNull((this as? Try.Failure)?.error) { "Expected a failure but got $this" }

    // --- From a URL ---

    @Test
    fun `a downloaded cover is stored once under the covers directory`() = runBlocking {
        val bytes = picture()
        serve(bytes)

        val path = store().saveFromUrl(server.url("/cover.jpg").toString()).path()

        assertEquals(coversDir.canonicalPath, File(path).parentFile?.canonicalPath)
        assertArrayEquals(bytes, File(path).readBytes())
        assertEquals(1, savedFiles().size)
    }

    @Test
    fun `two covers get different files`() = runBlocking {
        serve(picture())
        serve(picture(width = 130))

        val first = store().saveFromUrl(server.url("/a.jpg").toString()).path()
        val second = store().saveFromUrl(server.url("/b.jpg").toString()).path()

        assertNotEquals(first, second)
    }

    @Test
    fun `a one pixel placeholder is rejected and leaves nothing behind`() = runBlocking {
        serve(picture(width = 1, height = 1))

        val result = store().saveFromUrl(server.url("/blank.gif").toString())

        assertEquals(AppError.NotFound, result.error())
        assertTrue(savedFiles().isEmpty())
    }

    @Test
    fun `a small picture is rejected as a placeholder`() = runBlocking {
        serve(picture(width = 30, height = 40))

        assertEquals(
            AppError.NotFound,
            store().saveFromUrl(server.url("/s.jpg").toString()).error(),
        )
    }

    @Test
    fun `something that is not an image is rejected`() = runBlocking {
        serve("<html>Not found</html>".repeat(200).toByteArray())

        val result = store().saveFromUrl(server.url("/page").toString())

        assertEquals(AppError.NotFound, result.error())
        assertTrue(savedFiles().isEmpty())
    }

    @Test
    fun `a 404 means there is no cover`() = runBlocking {
        serve(ByteArray(0), code = 404)

        assertEquals(AppError.NotFound, store().saveFromUrl(server.url("/x").toString()).error())
        assertTrue(savedFiles().isEmpty())
    }

    @Test
    fun `a 429 means rate limited`() = runBlocking {
        serve(ByteArray(0), code = 429)

        assertEquals(AppError.RateLimited, store().saveFromUrl(server.url("/x").toString()).error())
    }

    @Test
    fun `a download over the size limit is rejected`() = runBlocking {
        serve(picture())

        val result = store(maxBytes = 1_000L).saveFromUrl(server.url("/big.jpg").toString())

        assertEquals(AppError.NotFound, result.error())
        assertTrue(savedFiles().isEmpty())
    }

    @Test
    fun `an unreachable server is offline`() = runBlocking {
        val url = server.url("/gone.jpg").toString()
        server.shutdown()

        assertEquals(AppError.Offline, store().saveFromUrl(url).error())
    }

    // --- From bytes (a cover the user picked) ---

    @Test
    fun `a picked cover is stored`() = runBlocking {
        val bytes = picture()

        val path = store().saveFromBytes(bytes).path()

        assertArrayEquals(bytes, File(path).readBytes())
    }

    @Test
    fun `a picked file that is not a usable image is invalid input`() = runBlocking {
        val result = store().saveFromBytes(ByteArray(5_000) { 7 })

        assertEquals(AppError.InvalidInput("cover"), result.error())
        assertTrue(savedFiles().isEmpty())
    }

    // --- Delete ---

    @Test
    fun `a stored cover can be deleted`() = runBlocking {
        val path = store().saveFromBytes(picture()).path()

        val result = store().delete(path)

        assertEquals(Try.Success(Unit), result)
        assertFalse(File(path).exists())
    }

    @Test
    fun `deleting a cover that is already gone succeeds`() = runBlocking {
        val path = File(coversDir, "missing.jpg").path

        assertEquals(Try.Success(Unit), store().delete(path))
    }

    @Test
    fun `a file outside the covers directory is never deleted`() = runBlocking {
        val outside = temp.newFile("precious.txt")

        val result = store().delete(outside.path)

        assertEquals(AppError.InvalidInput("path"), result.error())
        assertTrue(outside.exists())
    }

    @Test
    fun `a path that climbs out of the covers directory is refused`() = runBlocking {
        val outside = temp.newFile("precious.txt")
        coversDir.mkdirs()

        val sneaky = File(coversDir, "../${outside.name}").path

        assertEquals(AppError.InvalidInput("path"), store().delete(sneaky).error())
        assertTrue(outside.exists())
    }
}
