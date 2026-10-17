package app.otium.data.backup.drive

import app.otium.data.remote.RemoteClients
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.RemoteBackup
import app.otium.domain.port.DispatcherProvider
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val FILES = "/drive/v3/files"
private const val UPLOAD_START = "/upload/drive/v3/files"
private const val UPLOAD_SESSION = "/upload/session/"
private const val QUOTA_REASON = """{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}"""

class GoogleDriveBackupClientTest {

    /** A tiny Google Drive: just the calls the client makes, with files kept in memory. */
    private class FakeDrive : Dispatcher() {
        class Stored(
            val id: String,
            var name: String,
            val props: Map<String, String>,
            val bytes: ByteArray,
        )

        lateinit var origin: String

        /** Where upload sessions point; [origin] unless a test sends them elsewhere. */
        var uploadOrigin: String? = null
        val files = LinkedHashMap<String, Stored>()
        val log = CopyOnWriteArrayList<String>()
        val authorizations = CopyOnWriteArrayList<String?>()
        val createdParents = CopyOnWriteArrayList<List<String>>()
        var uploadedLength: String? = null

        /** Makes every checksum Drive reports wrong. */
        var corruptChecksums = false

        /** Leaves sha256Checksum out of Drive's file responses. */
        var omitChecksums = false
        var quotaLimit: String? = null
        var quotaUsage: String? = "0"

        private val sessions = ConcurrentHashMap<String, JsonObject>()
        private val failures = CopyOnWriteArrayList<Pair<(String) -> Boolean, MockResponse>>()
        private var nextId = 1

        fun failWith(code: Int, body: String = "", times: Int = 1, matching: (String) -> Boolean) {
            repeat(times) {
                failures += matching to MockResponse().setResponseCode(code).setBody(body)
            }
        }

        fun add(name: String, info: BackupInfo, content: String): Stored =
            Stored("f${nextId++}", name, info.toProperties(), content.toByteArray())
                .also { files[it.id] = it }

        fun named(name: String): Stored? = files.values.firstOrNull { it.name == name }

        fun names(): List<String> = files.values.map { it.name }

        fun count(key: String) = log.count { it == key }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
            val key = "${request.method} ${url.encodedPath}"
            log += key
            authorizations += request.getHeader("Authorization")
            failures.firstOrNull { it.first(key) }?.let {
                failures.remove(it)
                return it.second
            }
            return synchronized(this) { route(request, url, key) }
        }

        private fun route(request: RecordedRequest, url: HttpUrl, key: String): MockResponse {
            val path = url.encodedPath
            return when {
                key == "GET $FILES" -> json(buildJsonObject { put("files", filesJson()) })
                key == "GET /drive/v3/about" -> json(aboutJson())
                key == "POST $UPLOAD_START" -> startSession(request)
                request.method == "PUT" && path.startsWith(UPLOAD_SESSION) -> finish(request, path)
                path.startsWith("$FILES/") -> file(request, url, path.removePrefix("$FILES/"))
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun startSession(request: RecordedRequest): MockResponse {
            val metadata = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            createdParents +=
                metadata["parents"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            uploadedLength = request.getHeader("X-Upload-Content-Length")
            val id = "s${nextId++}"
            sessions[id] = metadata
            val target = uploadOrigin ?: origin
            return MockResponse().addHeader("Location", "$target$UPLOAD_SESSION$id")
        }

        private fun finish(request: RecordedRequest, path: String): MockResponse {
            val metadata = sessions.remove(path.removePrefix(UPLOAD_SESSION))
                ?: return MockResponse().setResponseCode(404)
            val props = metadata["appProperties"]?.jsonObject
                ?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val stored = Stored(
                id = "f${nextId++}",
                name = metadata["name"]?.jsonPrimitive?.content.orEmpty(),
                props = props,
                bytes = request.body.readByteArray(),
            )
            files[stored.id] = stored
            return json(fileJson(stored))
        }

        private fun file(request: RecordedRequest, url: HttpUrl, id: String): MockResponse {
            val stored = files[id] ?: return MockResponse().setResponseCode(404)
            return when (request.method) {
                "GET" ->
                    if (url.queryParameter("alt") == "media") {
                        MockResponse().setBody(Buffer().write(stored.bytes))
                    } else {
                        json(fileJson(stored))
                    }

                "PATCH" -> {
                    val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
                    stored.name = body["name"]?.jsonPrimitive?.content ?: stored.name
                    json(fileJson(stored))
                }

                "DELETE" -> {
                    files.remove(id)
                    MockResponse().setResponseCode(204)
                }

                else -> MockResponse().setResponseCode(405)
            }
        }

        private fun filesJson() = kotlinx.serialization.json.JsonArray(files.values.map(::fileJson))

        private fun fileJson(stored: Stored) = buildJsonObject {
            put("id", stored.id)
            put("name", stored.name)
            if (!omitChecksums) {
                put(
                    "sha256Checksum",
                    if (corruptChecksums) "0".repeat(64) else sha256(stored.bytes),
                )
            }
            put("appProperties", buildJsonObject { stored.props.forEach { (k, v) -> put(k, v) } })
        }

        private fun aboutJson() = buildJsonObject {
            put(
                "storageQuota",
                buildJsonObject {
                    quotaLimit?.let { put("limit", it) }
                    quotaUsage?.let { put("usage", it) }
                },
            )
        }

        private fun json(body: JsonObject) =
            MockResponse().addHeader("Content-Type", "application/json").setBody(body.toString())

        private fun json(body: kotlinx.serialization.json.JsonElement) =
            MockResponse().addHeader("Content-Type", "application/json").setBody(body.toString())

        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private object RealDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private val drive = FakeDrive()
    private var token: Try<String> = Try.Success("secret-token")

    /** What Google hands out once the first token was discarded. */
    private var fresh: Try<String> = Try.Success("fresh-token")
    private val tokens = FakeTokens()

    /** Hands out [token], then [fresh] after a discard, and records what the client reported. */
    private inner class FakeTokens : AccessTokenProvider {
        val discarded = CopyOnWriteArrayList<String>()
        var revoked = false

        override suspend fun accessToken(): Try<String> = if (discarded.isEmpty()) token else fresh

        override suspend fun discard(token: String) {
            discarded += token
        }

        override suspend fun revoked() {
            revoked = true
        }
    }

    @Before
    fun start() {
        drive.origin = server.url("/").toString().trimEnd('/')
        server.dispatcher = drive
        server.start()
    }

    @After
    fun stop() = runCatching { server.shutdown() }.let { }

    private val cacheDir by lazy { temp.newFolder("cache") }

    private fun client(maxDownloadBytes: Long = 512L * 1024 * 1024): GoogleDriveBackupClient {
        val http = RemoteClients.transferClient(RemoteClients.httpClient("test"))
        val api = RemoteClients.retrofit(server.url("/"), http).create(DriveApi::class.java)
        return GoogleDriveBackupClient(
            api = api,
            tokens = tokens,
            cacheDir = cacheDir,
            dispatchers = RealDispatchers,
            retryDelayMillis = 1,
            maxDownloadBytes = maxDownloadBytes,
            // The fake Drive is plain http on localhost; trust it and nothing else.
            trustsUploadAddress = { it.host == server.hostName && it.port == server.port },
        )
    }

    private fun localBackup(content: String): File = temp.newFile().apply { writeText(content) }

    private fun <T> run(
        maxDownloadBytes: Long = 512L * 1024 * 1024,
        block: suspend GoogleDriveBackupClient.() -> T,
    ): T = runBlocking { client(maxDownloadBytes).block() }

    private fun content(name: String) = drive.named(name)?.bytes?.decodeToString()

    // --- upload and rotation ---

    @Test
    fun `the first backup becomes the latest and nothing else exists`() {
        val info = backupInfo(createdAt = 10L)

        val result = run { upload(localBackup("first"), info) }

        assertEquals(Try.Success(Unit), result)
        assertEquals(listOf(DriveFileNames.LATEST), drive.names())
        assertEquals("first", content(DriveFileNames.LATEST))
        assertEquals(
            Try.Success(listOf(RemoteBackup(BackupSlot.LATEST, info))),
            run { list() },
        )
    }

    @Test
    fun `a second backup keeps the first as the previous one`() {
        val first = backupInfo(createdAt = 1L)
        val second = backupInfo(createdAt = 2L)
        run { upload(localBackup("first"), first) }

        run { upload(localBackup("second"), second) }

        assertEquals("second", content(DriveFileNames.LATEST))
        assertEquals("first", content(DriveFileNames.PREVIOUS))
        assertEquals(
            Try.Success(
                listOf(
                    RemoteBackup(BackupSlot.LATEST, second),
                    RemoteBackup(BackupSlot.PREVIOUS, first),
                ),
            ),
            run { list() },
        )
    }

    @Test
    fun `a third backup drops the oldest`() {
        run { upload(localBackup("one"), backupInfo(createdAt = 1L)) }
        run { upload(localBackup("two"), backupInfo(createdAt = 2L)) }

        run { upload(localBackup("three"), backupInfo(createdAt = 3L)) }

        assertEquals("three", content(DriveFileNames.LATEST))
        assertEquals("two", content(DriveFileNames.PREVIOUS))
        assertEquals(setOf(DriveFileNames.LATEST, DriveFileNames.PREVIOUS), drive.names().toSet())
    }

    @Test
    fun `the new file is complete before any older backup is touched`() {
        run { upload(localBackup("one"), backupInfo(createdAt = 1L)) }
        run { upload(localBackup("two"), backupInfo(createdAt = 2L)) }
        drive.log.clear()

        run { upload(localBackup("three"), backupInfo(createdAt = 3L)) }

        val sent = drive.log.indexOfFirst { it.startsWith("PUT $UPLOAD_SESSION") }
        val firstChange = drive.log.indexOfFirst {
            it.startsWith("DELETE") || it.startsWith("PATCH")
        }
        assertTrue("uploaded at $sent, first change at $firstChange", sent in 0 until firstChange)
    }

    @Test
    fun `a failed upload leaves the earlier backups exactly as they were`() {
        run { upload(localBackup("one"), backupInfo(createdAt = 1L)) }
        run { upload(localBackup("two"), backupInfo(createdAt = 2L)) }
        drive.failWith(500, times = 1) { it.startsWith("PUT $UPLOAD_SESSION") }

        val result = run { upload(localBackup("three"), backupInfo(createdAt = 3L)) }

        assertEquals(Try.Failure(AppError.RateLimited), result)
        assertEquals("two", content(DriveFileNames.LATEST))
        assertEquals("one", content(DriveFileNames.PREVIOUS))
        assertEquals(setOf(DriveFileNames.LATEST, DriveFileNames.PREVIOUS), drive.names().toSet())
    }

    @Test
    fun `a file Drive stored wrongly is removed and the backup fails`() {
        run { upload(localBackup("one"), backupInfo(createdAt = 1L)) }
        drive.corruptChecksums = true

        val result = run { upload(localBackup("two"), backupInfo(createdAt = 2L)) }

        assertTrue(result is Try.Failure)
        assertNull(drive.named(DriveFileNames.INCOMING))
        assertEquals(listOf(DriveFileNames.LATEST), drive.names())
        assertEquals("one", content(DriveFileNames.LATEST))
    }

    @Test
    fun `an upload for which Drive reports no checksum is removed and fails`() {
        run { upload(localBackup("one"), backupInfo(createdAt = 1L)) }
        drive.omitChecksums = true

        val result = run { upload(localBackup("two"), backupInfo(createdAt = 2L)) }

        assertTrue(result is Try.Failure)
        assertNull(drive.named(DriveFileNames.INCOMING))
        assertEquals(listOf(DriveFileNames.LATEST), drive.names())
        assertEquals("one", content(DriveFileNames.LATEST))
    }

    @Test
    fun `an upload that finished but was never renamed becomes the latest`() {
        val older = backupInfo(createdAt = 1L)
        val newer = backupInfo(createdAt = 2L)
        drive.add(DriveFileNames.PREVIOUS, older, "old")
        drive.add(DriveFileNames.INCOMING, newer, "new")

        assertEquals(
            Try.Success(
                listOf(
                    RemoteBackup(BackupSlot.LATEST, newer),
                    RemoteBackup(BackupSlot.PREVIOUS, older),
                ),
            ),
            run { list() },
        )

        run { upload(localBackup("newest"), backupInfo(createdAt = 3L)) }

        assertEquals("newest", content(DriveFileNames.LATEST))
        assertEquals("new", content(DriveFileNames.PREVIOUS))
        assertEquals(setOf(DriveFileNames.LATEST, DriveFileNames.PREVIOUS), drive.names().toSet())
    }

    @Test
    fun `a leftover incoming file next to a latest backup is dropped`() {
        drive.add(DriveFileNames.LATEST, backupInfo(createdAt = 2L), "latest")
        drive.add(DriveFileNames.INCOMING, backupInfo(createdAt = 3L), "stray")

        run { upload(localBackup("fresh"), backupInfo(createdAt = 4L)) }

        assertEquals("fresh", content(DriveFileNames.LATEST))
        assertEquals("latest", content(DriveFileNames.PREVIOUS))
        assertEquals(setOf(DriveFileNames.LATEST, DriveFileNames.PREVIOUS), drive.names().toSet())
    }

    @Test
    fun `an upload declares its size and goes to the app data folder only`() {
        run { upload(localBackup("twelve bytes"), backupInfo()) }

        assertEquals("12", drive.uploadedLength)
        assertEquals(listOf(listOf(APP_DATA_FOLDER)), drive.createdParents.toList())
        val calls = drive.log.map { it.substringAfter(' ') }
        assertTrue(calls.all { it.startsWith("/drive/v3/") || it.startsWith("/upload/") })
    }

    @Test
    fun `every call, the upload itself included, carries the token`() {
        run { upload(localBackup("data"), backupInfo()) }

        assertTrue(drive.authorizations.isNotEmpty())
        assertTrue(drive.authorizations.all { it == "Bearer secret-token" })
    }

    // --- download ---

    @Test
    fun `downloads the chosen backup to a temporary file`() {
        drive.add(DriveFileNames.LATEST, backupInfo(createdAt = 2L), "latest bytes")
        drive.add(DriveFileNames.PREVIOUS, backupInfo(createdAt = 1L), "previous bytes")

        val latest = (run { download(BackupSlot.LATEST) } as Try.Success).value
        val previous = (run { download(BackupSlot.PREVIOUS) } as Try.Success).value

        assertEquals("latest bytes", latest.readText())
        assertEquals("previous bytes", previous.readText())
        assertEquals(cacheDir, latest.parentFile)
    }

    @Test
    fun `a missing backup is not found`() {
        drive.add(DriveFileNames.LATEST, backupInfo(), "only one")

        assertEquals(Try.Failure(AppError.NotFound), run { download(BackupSlot.PREVIOUS) })
        assertEquals(emptyList<File>(), cacheDir.listFiles().orEmpty().toList())
    }

    @Test
    fun `a download that does not match its checksum is refused and removed`() {
        drive.add(DriveFileNames.LATEST, backupInfo(), "bytes")
        drive.corruptChecksums = true

        val result = run { download(BackupSlot.LATEST) }

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertEquals(emptyList<File>(), cacheDir.listFiles().orEmpty().toList())
    }

    @Test
    fun `a download with no checksum is refused and removed`() {
        drive.add(DriveFileNames.LATEST, backupInfo(), "bytes")
        drive.omitChecksums = true

        val result = run { download(BackupSlot.LATEST) }

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertEquals(emptyList<File>(), cacheDir.listFiles().orEmpty().toList())
    }

    @Test
    fun `a download over the size limit is refused and removed`() {
        drive.add(DriveFileNames.LATEST, backupInfo(), "0123456789")

        val result = run(maxDownloadBytes = 5) { download(BackupSlot.LATEST) }

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertEquals(emptyList<File>(), cacheDir.listFiles().orEmpty().toList())
    }

    // --- listing ---

    @Test
    fun `an empty Drive lists nothing`() {
        assertEquals(Try.Success(emptyList<RemoteBackup>()), run { list() })
    }

    @Test
    fun `files Otium did not write are ignored`() {
        drive.files["x"] = FakeDrive.Stored("x", DriveFileNames.LATEST, emptyMap(), byteArrayOf(1))

        assertEquals(Try.Success(emptyList<RemoteBackup>()), run { list() })
    }

    // --- free space ---

    @Test
    fun `free space is the limit minus what is used`() {
        drive.quotaLimit = "1000"
        drive.quotaUsage = "400"

        assertEquals(Try.Success(600L), run { freeSpace() })
    }

    @Test
    fun `an account without a limit has no cap`() {
        drive.quotaLimit = null

        assertEquals(Try.Success(null), run { freeSpace() })
    }

    @Test
    fun `an account over its limit has no space left`() {
        drive.quotaLimit = "1000"
        drive.quotaUsage = "1500"

        assertEquals(Try.Success(0L), run { freeSpace() })
    }

    // --- failures ---

    @Test
    fun `without access nothing is sent`() {
        token = Try.Failure(AppError.AuthExpired)

        assertEquals(Try.Failure(AppError.AuthExpired), run { list() })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a refused cached token is dropped and the call made again with a fresh one`() {
        drive.failWith(401) { it == "GET $FILES" }

        assertEquals(Try.Success(emptyList<RemoteBackup>()), run { list() })
        assertEquals(listOf("secret-token"), tokens.discarded)
        assertEquals("Bearer fresh-token", drive.authorizations.last())
        assertFalse(tokens.revoked)
    }

    @Test
    fun `a refused fresh token means the grant is gone`() {
        drive.failWith(401, times = 2) { it == "GET $FILES" }

        assertEquals(Try.Failure(AppError.AuthExpired), run { list() })
        assertEquals(2, drive.count("GET $FILES"))
        assertTrue(tokens.revoked)
    }

    @Test
    fun `no fresh token after a refusal is reported as is`() {
        drive.failWith(401) { it == "GET $FILES" }
        fresh = Try.Failure(AppError.Offline)

        assertEquals(Try.Failure(AppError.Offline), run { list() })
        assertFalse(tokens.revoked)
    }

    @Test
    fun `an upload refused part way is made again in full with a fresh token`() {
        drive.failWith(401) { it.startsWith("PUT $UPLOAD_SESSION") }

        val result = run { upload(localBackup("data"), backupInfo()) }

        assertEquals(Try.Success(Unit), result)
        assertEquals(listOf(DriveFileNames.LATEST), drive.names())
        assertEquals("data", content(DriveFileNames.LATEST))
    }

    @Test
    fun `a full Drive is reported when the upload starts`() {
        drive.failWith(403, QUOTA_REASON) { it == "POST $UPLOAD_START" }

        val result = run { upload(localBackup("data"), backupInfo()) }

        assertEquals(Try.Failure(AppError.InsufficientDriveSpace), result)
        assertTrue(drive.files.isEmpty())
    }

    @Test
    fun `an upload address on another host gets neither the token nor the file`() {
        val foreign = MockWebServer().apply { start() }
        try {
            drive.uploadOrigin = foreign.url("/").toString().trimEnd('/')

            val result = run { upload(localBackup("data"), backupInfo()) }

            assertEquals(
                Try.Failure(AppError.Unexpected("Drive gave an unexpected upload address")),
                result,
            )
            assertEquals(0, foreign.requestCount)
            assertTrue(drive.files.isEmpty())
        } finally {
            foreign.shutdown()
        }
    }

    @Test
    fun `a rate limit is told apart from missing permission`() {
        val limited = """{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}"""
        val denied = """{"error":{"errors":[{"reason":"insufficientPermissions"}]}}"""
        drive.failWith(403, limited) { it == "GET $FILES" }
        assertEquals(Try.Failure(AppError.RateLimited), run { list() })

        drive.failWith(403, denied) { it == "GET $FILES" }
        assertEquals(Try.Failure(AppError.AuthExpired), run { list() })
    }

    @Test
    fun `a busy Drive is asked again`() {
        drive.failWith(429, times = 1) { it == "GET $FILES" }
        drive.failWith(503, times = 1) { it == "GET $FILES" }

        assertEquals(Try.Success(emptyList<RemoteBackup>()), run { list() })
        assertEquals(3, drive.count("GET $FILES"))
    }

    @Test
    fun `a Drive that stays busy is reported after three tries`() {
        drive.failWith(503, times = 5) { it == "GET $FILES" }

        assertEquals(Try.Failure(AppError.RateLimited), run { list() })
        assertEquals(3, drive.count("GET $FILES"))
    }

    @Test
    fun `an answer that will not change is not asked again`() {
        drive.failWith(404, times = 5) { it == "GET $FILES" }

        assertEquals(Try.Failure(AppError.NotFound), run { list() })
        assertEquals(1, drive.count("GET $FILES"))
    }

    @Test
    fun `no connection is reported as offline`() {
        server.shutdown()

        assertEquals(Try.Failure(AppError.Offline), run { list() })
        assertFalse(cacheDir.listFiles().orEmpty().isNotEmpty())
    }
}
