package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBackupEncryption
import app.otium.domain.fakes.FakeBackupFiles
import app.otium.domain.fakes.FakeBackupPreferences
import app.otium.domain.fakes.FakeLibraryRestorer
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.RestorePreview
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBackupTest {

    private val restorer = FakeLibraryRestorer()
    private var lookupsScheduled = 0
    private val encryption = FakeBackupEncryption()
    private val restoreBackup =
        RestoreBackup(restorer, { lookupsScheduled++ }, encryption, BackupLock())

    private val info = backupInfo(createdAt = 4L, bookCount = 8)
    private val preview = RestorePreview(BackupSlot.LATEST, info, FakeBackupFiles.write(info))

    @Test
    fun `replaces the library with the backup`() = runTest {
        assertEquals(Try.Success(Unit), restoreBackup(preview))

        assertEquals(info, restorer.restored)
    }

    @Test
    fun `the downloaded file is removed once restored`() = runTest {
        restoreBackup(preview)

        assertFalse(preview.file.exists())
    }

    @Test
    fun `books still waiting for a lookup resume it`() = runTest {
        restoreBackup(preview)

        assertEquals(1, lookupsScheduled)
    }

    @Test
    fun `a failed restore leaves the library alone and keeps the file for another try`() = runTest {
        restorer.failure = AppError.Storage("no space")

        val result = restoreBackup(preview)

        assertEquals(Try.Failure(AppError.Storage("no space")), result)
        assertNull(restorer.restored)
        assertTrue(preview.file.exists())
        assertEquals(0, lookupsScheduled)
    }

    @Test
    fun `restoring an encrypted backup keeps new backups encrypted with its key`() = runTest {
        val keyId = FakeBackupEncryption.keyIdOf("correct horse")
        val encrypted = File.createTempFile("encrypted", ".bin").apply {
            deleteOnExit()
            writeText(FakeBackupEncryption.encryptedContent(info, "correct horse"))
        }
        encryption.decrypt(encrypted, "correct horse".toCharArray())

        restoreBackup(preview.copy(info = info.copy(keyId = keyId)))

        assertEquals(keyId, encryption.activeKeyId.value)
    }

    @Test
    fun `restoring a plain backup leaves encryption off`() = runTest {
        restoreBackup(preview)

        assertNull(encryption.activeKeyId.value)
    }

    @Test
    fun `a failed restore does not take over the backup's key`() = runTest {
        restorer.failure = AppError.Storage("no space")

        restoreBackup(preview.copy(info = info.copy(keyId = "key-x")))

        assertNull(encryption.activeKeyId.value)
    }
}
