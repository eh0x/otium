package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBackupArchiver
import app.otium.domain.fakes.FakeBackupEncryption
import app.otium.domain.fakes.FakeBackupFiles
import app.otium.domain.fakes.FakeDriveBackupClient
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareRestoreTest {

    private val appSchema = 3
    private val drive = FakeDriveBackupClient()
    private val encryption = FakeBackupEncryption()
    private val prepareRestore = PrepareRestore(drive, FakeBackupArchiver(), encryption, appSchema)

    @Test
    fun `downloads the latest backup and describes it`() = runTest {
        val info = backupInfo(createdAt = 7L, schemaVersion = 3)
        drive.put(BackupSlot.LATEST, info)

        val preview = (prepareRestore() as Try.Success).value

        assertEquals(BackupSlot.LATEST, preview.slot)
        assertEquals(info, preview.info)
        assertTrue("the file is kept for the restore", preview.file.exists())
    }

    @Test
    fun `can prepare the previous backup instead`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(createdAt = 2L))
        val older = backupInfo(createdAt = 1L)
        drive.put(BackupSlot.PREVIOUS, older)

        val preview = (prepareRestore(BackupSlot.PREVIOUS) as Try.Success).value

        assertEquals(older, preview.info)
    }

    @Test
    fun `reports not found when Drive holds no backup`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), prepareRestore())
        assertEquals(0, drive.downloads)
    }

    @Test
    fun `reports not found when the slot is empty`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo())

        assertEquals(Try.Failure(AppError.NotFound), prepareRestore(BackupSlot.PREVIOUS))
    }

    @Test
    fun `a backup from an older schema is accepted`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(schemaVersion = 1))

        assertTrue(prepareRestore() is Try.Success)
    }

    @Test
    fun `a backup from the same schema is accepted`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(schemaVersion = appSchema))

        assertTrue(prepareRestore() is Try.Success)
    }

    @Test
    fun `a backup from a newer schema is rejected without downloading it`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(schemaVersion = appSchema + 1))

        val result = prepareRestore()

        assertEquals(
            Try.Failure(AppError.IncompatibleBackup(backupSchema = 4, appSchema = 3)),
            result,
        )
        assertEquals(0, drive.downloads)
    }

    @Test
    fun `the schema in the file counts, not the one Drive was told`() = runTest {
        val claimed = backupInfo(schemaVersion = 1)
        val actual = backupInfo(schemaVersion = 9)
        drive.put(BackupSlot.LATEST, claimed, content = FakeBackupFiles.encode(actual))

        val result = prepareRestore()

        assertEquals(
            Try.Failure(AppError.IncompatibleBackup(backupSchema = 9, appSchema = 3)),
            result,
        )
        assertFalse(drive.downloaded.single().exists())
    }

    @Test
    fun `a damaged download is rejected and removed`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(), content = FakeBackupFiles.DAMAGED)

        val result = prepareRestore()

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertFalse(drive.downloaded.single().exists())
    }

    @Test
    fun `a failure reaching Drive is passed on`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo())
        drive.failure = AppError.Offline

        assertEquals(Try.Failure(AppError.Offline), prepareRestore())
    }

    @Test
    fun `discarding a preview deletes the downloaded file`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo())
        val preview = (prepareRestore() as Try.Success).value

        preview.discard()

        assertFalse(preview.file.exists())
    }

    // --- Encrypted backups ---

    private val plain = backupInfo(createdAt = 9L)
    private val keyId = FakeBackupEncryption.keyIdOf("correct horse")

    private fun putEncrypted() = drive.put(
        BackupSlot.LATEST,
        plain.copy(keyId = keyId),
        content = FakeBackupEncryption.encryptedContent(plain, "correct horse"),
    )

    @Test
    fun `an encrypted backup asks for its passphrase before downloading`() = runTest {
        putEncrypted()

        assertEquals(Try.Failure(AppError.PassphraseRequired), prepareRestore())
        assertEquals(0, drive.downloads)
    }

    @Test
    fun `the right passphrase opens an encrypted backup`() = runTest {
        putEncrypted()

        val preview = (prepareRestore(passphrase = "correct horse".toCharArray()) as Try.Success)
            .value

        assertEquals(plain.copy(keyId = keyId), preview.info)
        assertEquals(FakeBackupFiles.encode(plain), preview.file.readText())
        assertFalse("the encrypted download is removed", drive.downloaded.single().exists())
    }

    @Test
    fun `a wrong passphrase is reported and leaves no files behind`() = runTest {
        putEncrypted()

        val result = prepareRestore(passphrase = "wrong".toCharArray())

        assertEquals(Try.Failure(AppError.WrongPassphrase), result)
        assertFalse(drive.downloaded.single().exists())
    }

    @Test
    fun `an encrypted backup made on this phone opens without asking`() = runTest {
        encryption.turnOn("correct horse")
        putEncrypted()

        assertTrue(prepareRestore() is Try.Success)
    }

    @Test
    fun `a damaged encrypted backup is rejected`() = runTest {
        drive.put(BackupSlot.LATEST, plain.copy(keyId = keyId), content = FakeBackupFiles.DAMAGED)

        val result = prepareRestore(passphrase = "correct horse".toCharArray())

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertFalse(drive.downloaded.single().exists())
    }
}
