package app.otium.domain.fakes

import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSettings
import app.otium.domain.port.BackupPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBackupPreferences(initial: BackupSettings = BackupSettings(), failedRuns: Int = 0) :
    BackupPreferences {
    private val savedSettings = MutableStateFlow(initial)
    private val last = MutableStateFlow<BackupInfo?>(null)
    private var failed = failedRuns

    override val settings: Flow<BackupSettings> = savedSettings
    override val lastBackup: Flow<BackupInfo?> = last

    /** The stored values, for assertions. */
    val currentSettings: BackupSettings get() = savedSettings.value
    val currentLastBackup: BackupInfo? get() = last.value
    val currentFailedRuns: Int get() = failed

    override suspend fun saveSettings(settings: BackupSettings) {
        savedSettings.value = settings
    }

    override suspend fun recordBackup(info: BackupInfo) {
        last.value = info
        failed = 0
    }

    override suspend fun failedRuns(): Int = failed

    override suspend fun setFailedRuns(count: Int) {
        failed = count
    }
}
