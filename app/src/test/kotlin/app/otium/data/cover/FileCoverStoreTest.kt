package app.otium.data.cover

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.otium.data.remote.RemoteClients
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.DispatcherProvider
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
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
        resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver,
        client = RemoteClients.httpClient("Otium/test"),
        dispatchers = RealDispatchers,
        maxBytes = maxBytes,
        trustsCoverAddress = { it.host == server.hostName && it.port == server.port },
    )

    private fun defaultStore() = FileCoverStore(
        directory = coversDir,
        resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver,
        client = RemoteClients.httpClient("Otium/test"),
        dispatchers = RealDispatchers,
        maxBytes = 5_000_000L,
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
    fun `a download with dimensions exceeding the limit is rejected`() = runBlocking {
        serve(picture(width = 4_097, height = 60))
        serve(picture(width = 60, height = 4_097))

        assertEquals(
            AppError.NotFound,
            store().saveFromUrl(server.url("/wide.png").toString()).error(),
        )
        assertEquals(
            AppError.NotFound,
            store().saveFromUrl(server.url("/tall.png").toString()).error(),
        )
        assertTrue(savedFiles().isEmpty())
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

    @Test
    fun `an untrusted or cleartext cover address is rejected before calling the network`() =
        runBlocking {
            serve(picture())

            assertEquals(
                AppError.InvalidInput("url"),
                defaultStore().saveFromUrl(server.url("/cover.jpg").toString()).error(),
            )
            assertEquals(
                AppError.InvalidInput("url"),
                defaultStore().saveFromUrl("https://evil.example.com/cover.jpg").error(),
            )
            assertEquals(0, server.requestCount)
            assertTrue(savedFiles().isEmpty())
        }

    @Test
    fun `trusted cover addresses require https on an allowed catalog host`() {
        assertTrue(isTrustedCoverAddress("https://covers.openlibrary.org/b/id/1-L.jpg".toHttpUrl()))
        assertTrue(isTrustedCoverAddress("https://ia800100.us.archive.org/1.jpg".toHttpUrl()))
        assertTrue(isTrustedCoverAddress("https://books.google.com/books/content?id=1".toHttpUrl()))
        assertTrue(isTrustedCoverAddress("https://lh3.googleusercontent.com/cover".toHttpUrl()))
        assertFalse(isTrustedCoverAddress("http://covers.openlibrary.org/b/id/1-L.jpg".toHttpUrl()))
        assertFalse(isTrustedCoverAddress("https://evil.example.com/cover.jpg".toHttpUrl()))
        assertFalse(isTrustedCoverAddress("https://notarchive.org/cover.jpg".toHttpUrl()))
    }

    // --- From the device (gallery or camera) ---

    private fun onDevice(bytes: ByteArray, name: String = "${UUID.randomUUID()}.png"): String =
        temp.newFile(name).apply { writeBytes(bytes) }.toURI().toString()

    private fun decodedSize(path: String): Pair<Int, Int> {
        val image = ImageIO.read(File(path))
        return image.width to image.height
    }

    @Test
    fun `a photo on the device is stored as a cover`() = runBlocking {
        val path = store().saveFromDevice(onDevice(picture(width = 300, height = 450))).path()

        assertEquals(coversDir.canonicalFile, File(path).parentFile?.canonicalFile)
        assertEquals(300 to 450, decodedSize(path))
    }

    @Test
    fun `a large photo is scaled down to cover size, keeping its shape`() = runBlocking {
        val path = store().saveFromDevice(onDevice(picture(width = 2_400, height = 3_600))).path()

        val (width, height) = decodedSize(path)
        assertEquals(1_200, height)
        assertEquals(800, width)
    }

    @Test
    fun `a device file that is not a picture is invalid input`() = runBlocking {
        val result = store().saveFromDevice(onDevice(ByteArray(5_000) { 7 }))

        assertEquals(AppError.InvalidInput("cover"), result.error())
        assertTrue(savedFiles().isEmpty())
    }

    @Test
    fun `a location that cannot be read is invalid input`() = runBlocking {
        val result = store().saveFromDevice(File(temp.root, "missing.png").toURI().toString())

        assertEquals(AppError.InvalidInput("cover"), result.error())
    }

    @Test
    fun `a panoramic strip on device does not crash when scaled`() = runBlocking {
        val wideResult = store().saveFromDevice(onDevice(picture(width = 2_500, height = 1)))
        val tallResult = store().saveFromDevice(onDevice(picture(width = 1, height = 2_500)))

        assertEquals(AppError.InvalidInput("cover"), wideResult.error())
        assertEquals(AppError.InvalidInput("cover"), tallResult.error())
    }
}
