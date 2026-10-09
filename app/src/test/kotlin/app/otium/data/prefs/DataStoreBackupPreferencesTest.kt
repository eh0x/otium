package app.otium.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreBackupPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun store(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun preferences(name: String = "backup.preferences_pb") =
        DataStoreBackupPreferences(store(File(folder.root, name)))

    private val info = BackupInfo(
        createdAt = 1_700_000_000_000L,
        sizeBytes = 2_048L,
        bookCount = 42,
        appVersion = "0.1.0",
        schemaVersion = 1,
    )

    @Test
    fun `scheduled backups are off and Wi-Fi only until the user says otherwise`() = runTest {
        val settings = preferences().settings.first()

        assertEquals(BackupSettings(), settings)
        assertFalse(settings.autoBackup)
        assertTrue(settings.wifiOnly)
    }

    @Test
    fun `saved settings are read back`() = runTest {
        val prefs = preferences()
        val chosen = BackupSettings(
            autoBackup = true,
            frequency = BackupFrequency.DAILY,
            wifiOnly = false,
            chargingOnly = true,
        )

        prefs.saveSettings(chosen)

        assertEquals(chosen, prefs.settings.first())
    }

    @Test
    fun `there is no last backup before the first one`() = runTest {
        assertNull(preferences().lastBackup.first())
    }

    @Test
    fun `the last backup is remembered`() = runTest {
        val prefs = preferences()

        prefs.recordBackup(info)

        assertEquals(info, prefs.lastBackup.first())
    }

    @Test
    fun `a recorded backup clears the count of failed runs`() = runTest {
        val prefs = preferences()
        prefs.setFailedRuns(2)
        assertEquals(2, prefs.failedRuns())

        prefs.recordBackup(info)

        assertEquals(0, prefs.failedRuns())
    }

    @Test
    fun `choices survive a new instance reading the same file`() = runTest {
        val file = File(folder.root, "reopen.preferences_pb")
        val prefs = DataStoreBackupPreferences(store(file))
        prefs.saveSettings(BackupSettings(autoBackup = true))
        prefs.recordBackup(info)
        scope.cancel()

        val reopened = DataStoreBackupPreferences(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                produceFile = { file },
            ),
        )

        assertTrue(reopened.settings.first().autoBackup)
        assertEquals(info, reopened.lastBackup.first())
    }

    @Test
    fun `an encrypted backup is remembered with its key id, and a later plain one without`() =
        runTest {
            val prefs = preferences()
            val encrypted = info.copy(keyId = "0123456789abcdef0123456789abcdef")

            prefs.recordBackup(encrypted)
            assertEquals(encrypted, prefs.lastBackup.first())

            prefs.recordBackup(info)
            assertNull(prefs.lastBackup.first()?.keyId)
        }
}
