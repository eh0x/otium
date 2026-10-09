package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBackupArchiver
import app.otium.domain.fakes.FakeBackupEncryption
import app.otium.domain.fakes.FakeBackupPreferences
import app.otium.domain.fakes.FakeDriveBackupClient
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupSlot
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunBackupTest {

    private val info = backupInfo(createdAt = 5_000L, bookCount = 12)
    private val archiver = FakeBackupArchiver(info)
    private val drive = FakeDriveBackupClient()
    private val preferences = FakeBackupPreferences()
    private val encryption = FakeBackupEncryption()
    private val runBackup = RunBackup(drive, archiver, encryption, preferences, BackupLock())

    @Test
    fun `uploads the archive as the latest backup and remembers it`() = runTest {
        val result = runBackup()

        assertEquals(Try.Success(info), result)
        assertEquals(info, drive.infoIn(BackupSlot.LATEST))
        assertEquals(info, preferences.currentLastBackup)
    }

    @Test
    fun `the temporary archive is removed after an upload`() = runTest {
        runBackup()

        assertFalse(archiver.created.single().exists())
    }

    @Test
    fun `a second backup keeps the first as the previous one`() = runTest {
        val first = backupInfo(createdAt = 1L)
        drive.put(BackupSlot.LATEST, first)

        runBackup()

        assertEquals(first, drive.infoIn(BackupSlot.PREVIOUS))
        assertEquals(info, drive.infoIn(BackupSlot.LATEST))
    }

    @Test
    fun `nothing is uploaded when the archive cannot be made`() = runTest {
        archiver.createFailure = AppError.Storage("disk full")

        val result = runBackup()

        assertEquals(Try.Failure(AppError.Storage("disk full")), result)
        assertEquals(0, drive.uploads)
        assertNull(preferences.currentLastBackup)
    }

    @Test
    fun `an empty library is never uploaded over an earlier backup`() = runTest {
        archiver.info = backupInfo(bookCount = 0)
        val earlier = backupInfo(createdAt = 1L, bookCount = 40)
        drive.put(BackupSlot.LATEST, earlier)

        val result = runBackup()

        assertEquals(Try.Failure(AppError.NothingToBackUp), result)
        assertEquals(earlier, drive.infoIn(BackupSlot.LATEST))
        assertFalse(archiver.created.single().exists())
    }

    @Test
    fun `a full Drive is reported before anything is uploaded`() = runTest {
        archiver.info = backupInfo(sizeBytes = 10_000L)
        drive.free = 9_999L

        val result = runBackup()

        assertEquals(Try.Failure(AppError.InsufficientDriveSpace), result)
        assertEquals(0, drive.uploads)
        assertFalse(archiver.created.single().exists())
    }

    @Test
    fun `a Drive without a limit is fine`() = runTest {
        drive.free = null

        assertEquals(Try.Success(info), runBackup())
    }

    @Test
    fun `a failed upload changes nothing and removes the archive`() = runTest {
        val earlier = backupInfo(createdAt = 1L)
        drive.put(BackupSlot.LATEST, earlier)
        drive.failure = AppError.AuthExpired

        val result = runBackup()

        assertEquals(Try.Failure(AppError.AuthExpired), result)
        assertEquals(earlier, drive.infoIn(BackupSlot.LATEST))
        assertNull(preferences.currentLastBackup)
        assertFalse(archiver.created.single().exists())
    }

    @Test
    fun `a successful backup ends a streak of failed scheduled runs`() = runTest {
        preferences.setFailedRuns(2)

        runBackup()

        assertEquals(0, preferences.currentFailedRuns)
    }

    @Test
    fun `two backups never run at the same time`() = runTest {
        joinAll(launch { runBackup() }, launch { runBackup() })

        assertEquals(1, archiver.maxConcurrent)
        assertEquals(2, drive.uploads)
    }

    // --- Encryption ---

    @Test
    fun `with a passphrase set, only the encrypted archive is uploaded`() = runTest {
        encryption.turnOn("correct horse")

        val result = runBackup()

        val keyId = FakeBackupEncryption.keyIdOf("correct horse")
        val content = checkNotNull(drive.contentIn(BackupSlot.LATEST))
        assertTrue(content.startsWith("ENC:$keyId:"))
        val uploaded = checkNotNull(drive.infoIn(BackupSlot.LATEST))
        assertEquals(keyId, uploaded.keyId)
        assertEquals(Try.Success(uploaded), result)
        assertEquals(uploaded, preferences.currentLastBackup)
    }

    @Test
    fun `the recorded size is that of the encrypted file`() = runTest {
        encryption.turnOn("correct horse")

        runBackup()

        val content = checkNotNull(drive.contentIn(BackupSlot.LATEST))
        assertEquals(content.length.toLong(), drive.infoIn(BackupSlot.LATEST)?.sizeBytes)
    }

    @Test
    fun `both temporary files are removed after an encrypted upload`() = runTest {
        encryption.turnOn("correct horse")

        runBackup()

        assertFalse(archiver.created.single().exists())
        assertFalse(encryption.produced.single().exists())
    }

    @Test
    fun `the encrypted file is removed when the upload fails`() = runTest {
        encryption.turnOn("correct horse")
        drive.failure = AppError.Offline

        runBackup()

        assertFalse(encryption.produced.single().exists())
    }

    @Test
    fun `a lost key stops the backup instead of uploading it unencrypted`() = runTest {
        encryption.turnOn("correct horse")
        encryption.keyLost = true

        val result = runBackup()

        assertEquals(Try.Failure(AppError.PassphraseRequired), result)
        assertEquals(0, drive.uploads)
        assertNull(preferences.currentLastBackup)
        assertFalse(archiver.created.single().exists())
    }

    @Test
    fun `without a passphrase the backup is not encrypted`() = runTest {
        runBackup()

        assertNull(drive.infoIn(BackupSlot.LATEST)?.keyId)
        assertTrue(encryption.produced.isEmpty())
    }
}
