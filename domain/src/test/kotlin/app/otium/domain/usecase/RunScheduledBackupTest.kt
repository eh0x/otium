package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.fakes.FakeBackupArchiver
import app.otium.domain.fakes.FakeBackupEncryption
import app.otium.domain.fakes.FakeBackupPreferences
import app.otium.domain.fakes.FakeDriveBackupClient
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunScheduledBackupTest {

    private val archiver = FakeBackupArchiver(backupInfo(createdAt = 9L))
    private val drive = FakeDriveBackupClient()

    private fun scheduled(
        frequency: BackupFrequency = BackupFrequency.DAILY,
        enabled: Boolean = true,
        failedRuns: Int = 0,
    ): Pair<RunScheduledBackup, FakeBackupPreferences> {
        val preferences = FakeBackupPreferences(
            BackupSettings(autoBackup = enabled, frequency = frequency),
            failedRuns,
        )
        val runBackup =
            RunBackup(drive, archiver, FakeBackupEncryption(), preferences, BackupLock())
        return RunScheduledBackup(runBackup, preferences) to preferences
    }

    @Test
    fun `backs up and is done`() = runTest {
        val (run, preferences) = scheduled()

        assertEquals(ScheduledBackupOutcome.Done, run(attempt = 0))
        assertEquals(archiver.info, preferences.currentLastBackup)
    }

    @Test
    fun `does nothing once the user has turned it off`() = runTest {
        val (run, preferences) = scheduled(enabled = false)

        assertEquals(ScheduledBackupOutcome.Done, run(attempt = 0))
        assertEquals(0, drive.uploads)
        assertNull(preferences.currentLastBackup)
    }

    @Test
    fun `a success ends the streak of failed runs`() = runTest {
        val (run, preferences) = scheduled(failedRuns = 2)

        run(attempt = 0)

        assertEquals(0, preferences.currentFailedRuns)
    }

    @Test
    fun `a lost connection is retried later without counting`() = runTest {
        drive.failure = AppError.Offline
        val (run, preferences) = scheduled()

        assertEquals(ScheduledBackupOutcome.RetryLater, run(attempt = 0))
        assertEquals(0, preferences.currentFailedRuns)
    }

    @Test
    fun `gives up after the last attempt and counts the failed run`() = runTest {
        drive.failure = AppError.Timeout
        val (run, preferences) = scheduled()

        val outcome = run(attempt = RunScheduledBackup.MAX_ATTEMPTS - 1)

        assertEquals(ScheduledBackupOutcome.Failed(AppError.Timeout, notify = false), outcome)
        assertEquals(1, preferences.currentFailedRuns)
    }

    @Test
    fun `a failure only the user can fix is not retried`() = runTest {
        drive.failure = AppError.AuthExpired
        val (run, preferences) = scheduled()

        val outcome = run(attempt = 0)

        assertEquals(ScheduledBackupOutcome.Failed(AppError.AuthExpired, notify = false), outcome)
        assertEquals(1, preferences.currentFailedRuns)
    }

    @Test
    fun `the user is told when daily runs fail three times in a row`() = runTest {
        drive.failure = AppError.AuthExpired
        val (run, preferences) = scheduled(BackupFrequency.DAILY, failedRuns = 2)

        val outcome = run(attempt = 0)

        assertEquals(ScheduledBackupOutcome.Failed(AppError.AuthExpired, notify = true), outcome)
        assertEquals(3, preferences.currentFailedRuns)
    }

    @Test
    fun `the user is told when weekly runs fail twice in a row`() = runTest {
        drive.failure = AppError.AuthExpired
        val (run, _) = scheduled(BackupFrequency.WEEKLY, failedRuns = 1)

        assertEquals(
            ScheduledBackupOutcome.Failed(AppError.AuthExpired, notify = true),
            run(attempt = 0),
        )
    }

    @Test
    fun `the user is told once per streak`() = runTest {
        drive.failure = AppError.AuthExpired
        val (run, preferences) = scheduled(BackupFrequency.DAILY, failedRuns = 3)

        val outcome = run(attempt = 0)

        assertEquals(ScheduledBackupOutcome.Failed(AppError.AuthExpired, notify = false), outcome)
        assertEquals(4, preferences.currentFailedRuns)
    }

    @Test
    fun `an empty library is not a failure`() = runTest {
        archiver.info = backupInfo(bookCount = 0)
        val (run, preferences) = scheduled(failedRuns = 1)

        assertEquals(ScheduledBackupOutcome.Done, run(attempt = 0))
        assertEquals(1, preferences.currentFailedRuns)
    }
}
