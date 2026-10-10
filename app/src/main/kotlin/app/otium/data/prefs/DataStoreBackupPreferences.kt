package app.otium.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSettings
import app.otium.domain.port.BackupPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Keeps the backup choices and bookkeeping in Jetpack DataStore. They belong to this device and
 * are not part of a backup: restoring on a new phone starts with the defaults.
 */
class DataStoreBackupPreferences @Inject constructor(
    @BackupStateStore private val dataStore: DataStore<Preferences>,
) : BackupPreferences {

    override val settings: Flow<BackupSettings> = dataStore.data.map { preferences ->
        val defaults = BackupSettings()
        BackupSettings(
            autoBackup = preferences[AUTO_BACKUP] ?: defaults.autoBackup,
            frequency = BackupFrequency.entries.firstOrNull { it.name == preferences[FREQUENCY] }
                ?: defaults.frequency,
            wifiOnly = preferences[WIFI_ONLY] ?: defaults.wifiOnly,
            chargingOnly = preferences[CHARGING_ONLY] ?: defaults.chargingOnly,
        )
    }

    override val lastBackup: Flow<BackupInfo?> = dataStore.data.map { preferences ->
        preferences[LAST_CREATED_AT]?.let { createdAt ->
            BackupInfo(
                createdAt = createdAt,
                sizeBytes = preferences[LAST_SIZE] ?: 0L,
                bookCount = preferences[LAST_BOOKS] ?: 0,
                appVersion = preferences[LAST_APP_VERSION].orEmpty(),
                schemaVersion = preferences[LAST_SCHEMA] ?: 0,
                keyId = preferences[LAST_KEY_ID],
            )
        }
    }

    override suspend fun saveSettings(settings: BackupSettings) {
        dataStore.edit {
            it[AUTO_BACKUP] = settings.autoBackup
            it[FREQUENCY] = settings.frequency.name
            it[WIFI_ONLY] = settings.wifiOnly
            it[CHARGING_ONLY] = settings.chargingOnly
        }
    }

    override suspend fun recordBackup(info: BackupInfo) {
        dataStore.edit {
            it[LAST_CREATED_AT] = info.createdAt
            it[LAST_SIZE] = info.sizeBytes
            it[LAST_BOOKS] = info.bookCount
            it[LAST_APP_VERSION] = info.appVersion
            it[LAST_SCHEMA] = info.schemaVersion
            val keyId = info.keyId
            if (keyId == null) it.remove(LAST_KEY_ID) else it[LAST_KEY_ID] = keyId
            it[FAILED_RUNS] = 0
        }
    }

    override suspend fun failedRuns(): Int = dataStore.data.first()[FAILED_RUNS] ?: 0

    override suspend fun setFailedRuns(count: Int) {
        dataStore.edit { it[FAILED_RUNS] = count }
    }

    private companion object {
        val AUTO_BACKUP = booleanPreferencesKey("backup_auto")
        val FREQUENCY = stringPreferencesKey("backup_frequency")
        val WIFI_ONLY = booleanPreferencesKey("backup_wifi_only")
        val CHARGING_ONLY = booleanPreferencesKey("backup_charging_only")
        val LAST_CREATED_AT = longPreferencesKey("backup_last_created_at")
        val LAST_SIZE = longPreferencesKey("backup_last_size")
        val LAST_BOOKS = intPreferencesKey("backup_last_books")
        val LAST_APP_VERSION = stringPreferencesKey("backup_last_app_version")
        val LAST_SCHEMA = intPreferencesKey("backup_last_schema")
        val LAST_KEY_ID = stringPreferencesKey("backup_last_key_id")
        val FAILED_RUNS = intPreferencesKey("backup_failed_runs")
    }
}
