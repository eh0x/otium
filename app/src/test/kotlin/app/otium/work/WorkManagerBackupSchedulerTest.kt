package app.otium.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerBackupSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scheduler = WorkManagerBackupScheduler(context)

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    private fun planned(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(BackupWorker.UNIQUE_NAME)
        .get()

    @Test
    fun `by default a backup waits for Wi-Fi`() {
        scheduler.schedule(BackupSettings(autoBackup = true))

        val work = planned().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.UNMETERED, work.constraints.requiredNetworkType)
        assertFalse(work.constraints.requiresCharging())
    }

    @Test
    fun `the user can allow mobile data and require charging`() {
        scheduler.schedule(BackupSettings(autoBackup = true, wifiOnly = false, chargingOnly = true))

        val work = planned().single()
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertTrue(work.constraints.requiresCharging())
    }

    @Test
    fun `the period follows the chosen frequency`() {
        scheduler.schedule(BackupSettings(autoBackup = true, frequency = BackupFrequency.DAILY))

        assertEquals(DAY_MILLIS, planned().single().periodicityInfo?.repeatIntervalMillis)

        scheduler.schedule(BackupSettings(autoBackup = true, frequency = BackupFrequency.WEEKLY))

        assertEquals(7 * DAY_MILLIS, planned().single().periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun `changing the settings leaves one plan, not two`() {
        scheduler.schedule(BackupSettings(autoBackup = true))
        scheduler.schedule(BackupSettings(autoBackup = true, wifiOnly = false))

        val work = planned().single()
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun `turning backups off cancels the plan`() {
        scheduler.schedule(BackupSettings(autoBackup = true))

        scheduler.cancel()

        assertEquals(WorkInfo.State.CANCELLED, planned().single().state)
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
