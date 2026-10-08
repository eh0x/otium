package app.otium.domain.usecase

import app.otium.domain.fakes.FakeBackupPreferences
import app.otium.domain.fakes.FakeBackupScheduler
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigureAutoBackupTest {

    private val preferences = FakeBackupPreferences()
    private val scheduler = FakeBackupScheduler()
    private val configure = ConfigureAutoBackup(preferences, scheduler)

    @Test
    fun `turning it on saves the choices and plans backups`() = runTest {
        val settings = BackupSettings(
            autoBackup = true,
            frequency = BackupFrequency.DAILY,
            wifiOnly = false,
            chargingOnly = true,
        )

        configure(settings)

        assertEquals(settings, preferences.currentSettings)
        assertEquals(settings, scheduler.scheduled)
    }

    @Test
    fun `changing a choice replaces the plan`() = runTest {
        configure(BackupSettings(autoBackup = true, frequency = BackupFrequency.DAILY))
        val weekly = BackupSettings(autoBackup = true, frequency = BackupFrequency.WEEKLY)

        configure(weekly)

        assertEquals(weekly, scheduler.scheduled)
    }

    @Test
    fun `turning it off keeps the choices but stops the schedule`() = runTest {
        configure(BackupSettings(autoBackup = true, wifiOnly = false))

        configure(BackupSettings(autoBackup = false, wifiOnly = false))

        assertNull(scheduler.scheduled)
        assertEquals(false, preferences.currentSettings.wifiOnly)
    }
}
