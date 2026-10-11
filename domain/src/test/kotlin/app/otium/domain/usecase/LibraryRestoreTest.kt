package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBackupArchiver
import app.otium.domain.fakes.FakeBackupEncryption
import app.otium.domain.fakes.FakeBackupFiles
import app.otium.domain.fakes.FakeDriveBackupClient
import app.otium.domain.fakes.FakeLibraryRestorer
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupSlot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRestoreTest {

    private val appSchema = 3
    private val drive = FakeDriveBackupClient()
    private val encryption = FakeBackupEncryption()
    private val restorer = FakeLibraryRestorer()
    private var lookupsScheduled = 0
    private val restore = LibraryRestore(
        drive = drive,
        archiver = FakeBackupArchiver(),
        restorer = restorer,
        encryption = encryption,
        lookups = { lookupsScheduled++ },
        lock = BackupLock(),
        appSchemaVersion = appSchema,
    )

    private val info = backupInfo(createdAt = 4L, bookCount = 8, schemaVersion = appSchema)

    private suspend fun prepared(): Try<*> {
        drive.put(BackupSlot.LATEST, info)
        return restore.prepare()
    }

    // --- Preparing ---

    @Test
    fun `downloads the latest backup and describes it`() = runTest {
        val described = backupInfo(createdAt = 7L, schemaVersion = 3)
        drive.put(BackupSlot.LATEST, described)

        val preview = (restore.prepare() as Try.Success).value

        assertEquals(BackupSlot.LATEST, preview.slot)
        assertEquals(described, preview.info)
        assertTrue("the download is kept for the restore", drive.downloaded.single().exists())
    }

    @Test
    fun `can prepare the previous backup instead`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(createdAt = 2L))
        val older = backupInfo(createdAt = 1L)
        drive.put(BackupSlot.PREVIOUS, older)

        val preview = (restore.prepare(BackupSlot.PREVIOUS) as Try.Success).value

        assertEquals(older, preview.info)
    }

    @Test
    fun `reports not found when Drive holds no backup`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), restore.prepare())
        assertEquals(0, drive.downloads)
    }

    @Test
    fun `reports not found when the slot is empty`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo())

        assertEquals(Try.Failure(AppError.NotFound), restore.prepare(BackupSlot.PREVIOUS))
    }

    @Test
    fun `a backup from an older schema is accepted`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(schemaVersion = 1))

        assertTrue(restore.prepare() is Try.Success)
    }

    @Test
    fun `a backup from the same schema is accepted`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(schemaVersion = appSchema))

        assertTrue(restore.prepare() is Try.Success)
    }

    @Test
    fun `a backup from a newer schema is rejected without downloading it`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(schemaVersion = appSchema + 1))

        val result = restore.prepare()

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

        val result = restore.prepare()

        assertEquals(
            Try.Failure(AppError.IncompatibleBackup(backupSchema = 9, appSchema = 3)),
            result,
        )
        assertFalse(drive.downloaded.single().exists())
    }

    @Test
    fun `a damaged download is rejected and removed`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(), content = FakeBackupFiles.DAMAGED)

        val result = restore.prepare()

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertFalse(drive.downloaded.single().exists())
    }

    @Test
    fun `a failure reaching Drive is passed on`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo())
        drive.failure = AppError.Offline

        assertEquals(Try.Failure(AppError.Offline), restore.prepare())
    }

    @Test
    fun `cancelling deletes the download and leaves nothing to restore`() = runTest {
        prepared()

        restore.cancel()

        assertFalse(drive.downloaded.single().exists())
        assertEquals(Try.Failure(AppError.NotFound), restore.confirm())
        assertNull(restorer.restored)
    }

    @Test
    fun `preparing again replaces the earlier download`() = runTest {
        drive.put(BackupSlot.LATEST, info)
        val older = backupInfo(createdAt = 1L)
        drive.put(BackupSlot.PREVIOUS, older)
        restore.prepare(BackupSlot.LATEST)

        restore.prepare(BackupSlot.PREVIOUS)
        restore.confirm()

        assertFalse(drive.downloaded.first().exists())
        assertEquals(older, restorer.restored)
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

        assertEquals(Try.Failure(AppError.PassphraseRequired), restore.prepare())
        assertEquals(0, drive.downloads)
    }

    @Test
    fun `the right passphrase opens an encrypted backup`() = runTest {
        putEncrypted()

        val preview =
            (restore.prepare(passphrase = "correct horse".toCharArray()) as Try.Success).value
        restore.confirm()

        assertEquals(plain.copy(keyId = keyId), preview.info)
        assertFalse("the encrypted download is removed", drive.downloaded.single().exists())
        assertEquals("the decrypted backup is what is restored", plain, restorer.restored)
    }

    @Test
    fun `a wrong passphrase is reported and leaves no files behind`() = runTest {
        putEncrypted()

        val result = restore.prepare(passphrase = "wrong".toCharArray())

        assertEquals(Try.Failure(AppError.WrongPassphrase), result)
        assertFalse(drive.downloaded.single().exists())
    }

    @Test
    fun `an encrypted backup made on this phone opens without asking`() = runTest {
        encryption.turnOn("correct horse")
        putEncrypted()

        assertTrue(restore.prepare() is Try.Success)
    }

    @Test
    fun `a damaged encrypted backup is rejected`() = runTest {
        drive.put(BackupSlot.LATEST, plain.copy(keyId = keyId), content = FakeBackupFiles.DAMAGED)

        val result = restore.prepare(passphrase = "correct horse".toCharArray())

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertFalse(drive.downloaded.single().exists())
    }

    // --- Confirming ---

    @Test
    fun `replaces the library with the backup`() = runTest {
        prepared()

        assertEquals(Try.Success(Unit), restore.confirm())

        assertEquals(info, restorer.restored)
    }

    @Test
    fun `the download is removed once restored`() = runTest {
        prepared()

        restore.confirm()

        assertFalse(drive.downloaded.single().exists())
    }

    @Test
    fun `books still waiting for a lookup resume it`() = runTest {
        prepared()

        restore.confirm()

        assertEquals(1, lookupsScheduled)
    }

    @Test
    fun `a failed restore leaves the library alone and drops the download`() = runTest {
        prepared()
        restorer.failure = AppError.Storage("no space")

        val result = restore.confirm()

        assertEquals(Try.Failure(AppError.Storage("no space")), result)
        assertNull(restorer.restored)
        assertEquals(0, lookupsScheduled)
        assertFalse(drive.downloaded.single().exists())
        assertEquals(Try.Failure(AppError.NotFound), restore.confirm())
    }

    @Test
    fun `restoring an encrypted backup keeps new backups encrypted with its key`() = runTest {
        putEncrypted()
        restore.prepare(passphrase = "correct horse".toCharArray())

        restore.confirm()

        assertEquals(keyId, encryption.activeKeyId.value)
    }

    @Test
    fun `restoring a plain backup leaves encryption off`() = runTest {
        prepared()

        restore.confirm()

        assertNull(encryption.activeKeyId.value)
    }

    @Test
    fun `a failed restore does not take over the backup's key`() = runTest {
        putEncrypted()
        restore.prepare(passphrase = "correct horse".toCharArray())
        restorer.failure = AppError.Storage("no space")

        restore.confirm()

        assertNull(encryption.activeKeyId.value)
    }

    @Test
    fun `confirming with nothing prepared is reported and changes nothing`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), restore.confirm())

        assertNull(restorer.restored)
        assertEquals(0, lookupsScheduled)
    }

    @Test
    fun `a restore is applied once`() = runTest {
        prepared()
        restore.confirm()

        assertEquals(Try.Failure(AppError.NotFound), restore.confirm())
        assertEquals(1, lookupsScheduled)
    }
}
