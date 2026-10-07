package app.otium.data.backup.archive

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupZipTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val manifest = BackupManifest(
        format = BackupManifest.FORMAT,
        appVersion = "0.1.0",
        schemaVersion = 2,
        createdAt = 1_700_000_000_000L,
        bookCount = 3,
    )
    private val zip = BackupZip()

    private fun file(name: String, bytes: ByteArray): File =
        File(temp.root, name).apply { writeBytes(bytes) }

    /** A zip with exactly the given entries, for building backups no honest app would write. */
    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val target = File(temp.newFolder(), "crafted.zip")
        ZipOutputStream(target.outputStream()).use { out ->
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return target
    }

    private val manifestBytes
        get() = manifestJson().toByteArray()

    private fun manifestJson(extra: String = "") =
        """{"format":1,"appVersion":"0.1.0","schemaVersion":2,"createdAt":5,"bookCount":1$extra}"""

    private fun extractTo(archive: File): Pair<Try<Extracted>, File> {
        val into = temp.newFolder()
        return zip.extract(archive, into) to into
    }

    @Test
    fun `a written backup unpacks to the same database and covers`() {
        val database = file("source.db", ByteArray(5_000) { (it % 251).toByte() })
        val coverOne = file("one.jpg", byteArrayOf(1, 2, 3))
        val coverTwo = file("two.jpg", byteArrayOf(9, 8))
        val target = File(temp.root, "backup.zip")

        zip.write(target, manifest, database, listOf(coverOne, coverTwo))
        val (result, into) = extractTo(target)

        val extracted = (result as Try.Success).value
        assertEquals(manifest, extracted.manifest)
        assertArrayEquals(database.readBytes(), extracted.database.readBytes())
        assertEquals(setOf("one.jpg", "two.jpg"), extracted.covers.map { it.name }.toSet())
        assertArrayEquals(byteArrayOf(1, 2, 3), File(into, "covers/one.jpg").readBytes())
        assertArrayEquals(byteArrayOf(9, 8), File(into, "covers/two.jpg").readBytes())
    }

    @Test
    fun `a library without covers round trips`() {
        val target = File(temp.root, "backup.zip")

        zip.write(target, manifest, file("source.db", byteArrayOf(7)), emptyList())

        val extracted = (extractTo(target).first as Try.Success).value
        assertTrue(extracted.covers.isEmpty())
    }

    @Test
    fun `the manifest can be read without unpacking`() {
        val target = File(temp.root, "backup.zip")
        zip.write(target, manifest, file("source.db", byteArrayOf(7)), emptyList())

        assertEquals(Try.Success(manifest), zip.readManifest(target))
    }

    @Test
    fun `the manifest converts to the info the domain shows`() {
        val info = manifest.toInfo(sizeBytes = 99L)

        assertEquals(99L, info.sizeBytes)
        assertEquals(3, info.bookCount)
        assertEquals(2, info.schemaVersion)
        assertEquals("0.1.0", info.appVersion)
        assertEquals(1_700_000_000_000L, info.createdAt)
    }

    @Test
    fun `a manifest with fields from a later version still reads`() {
        val later = zipOf("manifest.json" to manifestJson(""","new":true""").toByteArray())

        assertTrue(zip.readManifest(later) is Try.Success)
    }

    @Test
    fun `a newer layout is flagged as not readable`() {
        assertTrue(manifest.isReadable)
        assertFalse(manifest.copy(format = BackupManifest.FORMAT + 1).isReadable)
        assertFalse(manifest.copy(format = 0).isReadable)
    }

    @Test
    fun `entries that climb out of the target folder are refused`() {
        val names = listOf(
            "../evil.db",
            "covers/../../evil.jpg",
            "/etc/evil",
            "covers/..\\evil.jpg",
            "covers/sub/evil.jpg",
            "other/library.db",
        )
        for (name in names) {
            val archive = zipOf(
                "manifest.json" to manifestBytes,
                "library.db" to byteArrayOf(1),
                name to byteArrayOf(2),
            )
            val into = temp.newFolder()

            val result = zip.extract(archive, into)

            assertEquals("$name should be refused", Try.Failure(AppError.CorruptBackup), result)
            assertTrue("$name left files behind", into.listFiles().isNullOrEmpty())
            assertFalse(File(into.parentFile, "evil.db").exists())
            assertFalse(File(into.parentFile.parentFile, "evil.jpg").exists())
        }
    }

    @Test
    fun `a cover name with a leading dot is refused`() {
        val archive = zipOf(
            "manifest.json" to manifestBytes,
            "library.db" to byteArrayOf(1),
            "covers/.hidden" to byteArrayOf(2),
        )

        assertEquals(Try.Failure(AppError.CorruptBackup), extractTo(archive).first)
    }

    @Test
    fun `a backup without a database is refused`() {
        val archive = zipOf("manifest.json" to manifestBytes)

        assertEquals(Try.Failure(AppError.CorruptBackup), extractTo(archive).first)
    }

    @Test
    fun `a backup without a manifest is refused`() {
        val archive = zipOf("library.db" to byteArrayOf(1))

        assertEquals(Try.Failure(AppError.CorruptBackup), extractTo(archive).first)
        assertEquals(Try.Failure(AppError.CorruptBackup), zip.readManifest(archive))
    }

    @Test
    fun `a manifest that is not json is refused`() {
        val archive = zipOf("manifest.json" to "nope".toByteArray(), "library.db" to byteArrayOf(1))

        assertEquals(Try.Failure(AppError.CorruptBackup), zip.readManifest(archive))
        assertEquals(Try.Failure(AppError.CorruptBackup), extractTo(archive).first)
    }

    @Test
    fun `a file that is not a zip is refused`() {
        val notZip = file("plain.zip", "just some text".toByteArray())

        assertEquals(Try.Failure(AppError.CorruptBackup), zip.readManifest(notZip))
        assertEquals(Try.Failure(AppError.CorruptBackup), extractTo(notZip).first)
    }

    @Test
    fun `a truncated backup is refused`() {
        val target = File(temp.root, "backup.zip")
        zip.write(
            target,
            manifest,
            file(
                "source.db",
                ByteArray(20_000) {
                    it.toByte()
                },
            ),
            emptyList(),
        )
        val cut = file("cut.zip", target.readBytes().copyOf((target.length() / 2).toInt()))

        assertEquals(Try.Failure(AppError.CorruptBackup), extractTo(cut).first)
    }

    @Test
    fun `too many entries are refused`() {
        val small = BackupZip(maxEntries = 3)
        val archive = zipOf(
            "manifest.json" to manifestBytes,
            "library.db" to byteArrayOf(1),
            "covers/a.jpg" to byteArrayOf(1),
            "covers/b.jpg" to byteArrayOf(1),
        )

        assertEquals(Try.Failure(AppError.CorruptBackup), small.extract(archive, temp.newFolder()))
    }

    @Test
    fun `unpacking stops at the size limit however small the zip is`() {
        val small = BackupZip(maxUnpackedBytes = 1_000)
        val archive = zipOf(
            "manifest.json" to manifestBytes,
            "library.db" to ByteArray(100_000), // compresses to almost nothing
        )
        val into = temp.newFolder()

        assertEquals(Try.Failure(AppError.CorruptBackup), small.extract(archive, into))
        assertTrue(into.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a failed write leaves no partial file`() {
        val target = File(temp.root, "backup.zip")
        val missingDatabase = File(temp.root, "missing.db")

        val outcome = runCatching { zip.write(target, manifest, missingDatabase, emptyList()) }

        assertTrue(outcome.isFailure)
        assertFalse(target.exists())
    }

    @Test
    fun `covers with names a restore would refuse are left out when writing`() {
        val target = File(temp.root, "backup.zip")
        val odd = file(".nomedia", byteArrayOf(1))
        val fine = file("fine.jpg", byteArrayOf(2))

        zip.write(target, manifest, file("source.db", byteArrayOf(7)), listOf(odd, fine))

        val extracted = (extractTo(target).first as Try.Success).value
        assertEquals(listOf("fine.jpg"), extracted.covers.map { it.name })
    }
}
