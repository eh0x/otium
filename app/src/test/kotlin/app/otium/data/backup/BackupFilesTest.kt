package app.otium.data.backup

import app.otium.domain.usecase.BackupLock
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val cache by lazy { temp.newFolder("cache") }
    private val work by lazy { File(cache, "backup").apply { mkdirs() } }
    private val covers by lazy { temp.newFolder("covers") }
    private val files by lazy { BackupFiles(work, covers) }

    @Test
    fun `purge deletes leftover archives, unpacked folders and drive downloads`() = runTest {
        val plainBackup = File(work, "otium-backup123.zip").apply { writeText("plain zip") }
        val decryptedRestore = File(work, "otium-restore456.zip").apply { writeText("decrypted") }
        val unpacked = File(work, "restore-uuid").apply {
            mkdirs()
            File(this, "library.db").writeText("sqlite bytes")
        }
        val driveDownload = File(cache, "restore789.zip").apply { writeText("downloaded") }
        val otherCache = File(cache, "other.tmp").apply { writeText("keep") }
        val cover = File(covers, "hobbit.jpg").apply { writeText("keep cover") }

        val deleted = files.purgeNow(BackupLock())

        assertEquals(4, deleted)
        assertFalse(plainBackup.exists())
        assertFalse(decryptedRestore.exists())
        assertFalse(unpacked.exists())
        assertFalse(driveDownload.exists())
        assertTrue(otherCache.exists())
        assertTrue(cover.exists())
    }

    @Test
    fun `purge waits for a running backup or restore before emptying the work folder`() = runTest {
        val lock = BackupLock()
        var cleanedWhileLocked = false

        lock.withLock {
            val active = File(work, "otium-backup-active.zip").apply { writeText("in flight") }
            cleanedWhileLocked = !active.exists()
            active.delete()
        }
        files.purgeNow(lock)

        assertFalse(cleanedWhileLocked)
    }
}
