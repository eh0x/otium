package app.otium.domain.port

import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSettings
import kotlinx.coroutines.flow.Flow

/** Per-device backup choices and bookkeeping. Not part of the backup itself. */
interface BackupPreferences {
    val settings: Flow<BackupSettings>

    /** The last backup this device uploaded, or null before the first one. */
    val lastBackup: Flow<BackupInfo?>

    suspend fun saveSettings(settings: BackupSettings)

    /** Remembers [info] as the last backup and clears the count of failed scheduled runs. */
    suspend fun recordBackup(info: BackupInfo)

    /** Scheduled runs in a row that gave up without a backup. */
    suspend fun failedRuns(): Int

    suspend fun setFailedRuns(count: Int)

    /**
     * True once the owner has been offered backup encryption. The offer is made once, when Drive
     * is connected without it (D32), and never repeated.
     */
    val encryptionSuggested: Flow<Boolean>

    suspend fun markEncryptionSuggested()
}
