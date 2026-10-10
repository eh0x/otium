package app.otium.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.otium.domain.error.AppError
import app.otium.domain.fakes.FakeBackupArchiver
import app.otium.domain.fakes.FakeBackupEncryption
import app.otium.domain.fakes.FakeBackupPreferences
import app.otium.domain.fakes.FakeDriveBackupClient
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupSettings
import app.otium.domain.usecase.BackupLock
import app.otium.domain.usecase.RunBackup
import app.otium.domain.usecase.RunScheduledBackup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BackupWorkerTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val drive = FakeDriveBackupClient()
    private val archiver = FakeBackupArchiver()
    private val settings = BackupSettings(autoBackup = true, frequency = BackupFrequency.DAILY)

    private fun preferences(failedRuns: Int = 0) = FakeBackupPreferences(settings, failedRuns)

    private fun run(prefs: FakeBackupPreferences, attempt: Int = 0): ListenableWorker.Result {
        val runBackup = RunBackup(drive, archiver, FakeBackupEncryption(), prefs, BackupLock())
        val scheduled = RunScheduledBackup(runBackup, prefs)
        return runBlocking {
            TestListenableWorkerBuilder<BackupWorker>(application)
                .setRunAttemptCount(attempt)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ) = BackupWorker(
                            appContext,
                            workerParameters,
                            scheduled,
                            BackupNotifier(appContext),
                        )
                    },
                )
                .build()
                .doWork()
        }
    }

    private val notifications: NotificationManager
        get() = application.getSystemService(NotificationManager::class.java)

    @Before
    fun allowNotifications() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `a backup that goes through is a success and is remembered`() {
        archiver.info = backupInfo(bookCount = 7)
        val prefs = preferences()

        assertEquals(ListenableWorker.Result.success(), run(prefs))
        assertEquals(7, prefs.currentLastBackup?.bookCount)
        assertEquals(1, drive.uploads)
    }

    @Test
    fun `a lost connection is retried later`() {
        drive.failure = AppError.Offline

        assertEquals(ListenableWorker.Result.retry(), run(preferences()))
        assertNull(notifications.activeNotifications.firstOrNull())
    }

    @Test
    fun `a failure the user must fix ends the run quietly at first`() {
        drive.failure = AppError.AuthExpired
        val prefs = preferences()

        assertEquals(ListenableWorker.Result.success(), run(prefs))
        assertEquals(1, prefs.currentFailedRuns)
        assertNull(notifications.activeNotifications.firstOrNull())
    }

    @Test
    fun `the user is told once enough runs in a row have failed`() {
        drive.failure = AppError.AuthExpired
        val prefs = preferences(failedRuns = BackupFrequency.DAILY.failedRunsBeforeNotice - 1)

        assertEquals(ListenableWorker.Result.success(), run(prefs))
        assertNotNull(notifications.activeNotifications.firstOrNull())
    }

    @Test
    fun `an empty library is not a failure`() {
        archiver.info = backupInfo(bookCount = 0)
        val prefs = preferences()

        assertEquals(ListenableWorker.Result.success(), run(prefs))
        assertEquals(0, prefs.currentFailedRuns)
        assertEquals(0, drive.uploads)
    }
}
