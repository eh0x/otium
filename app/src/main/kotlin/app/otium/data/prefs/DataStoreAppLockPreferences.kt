package app.otium.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otium.domain.model.AppLockSettings
import app.otium.domain.model.LockTimeout
import app.otium.domain.port.AppLockPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Stores the app lock choice in the device's preferences, which Android backup and device
 * transfer leave out, so a new phone starts unlocked until its owner turns the lock on there.
 */
class DataStoreAppLockPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppLockPreferences {

    override val settings: Flow<AppLockSettings> = dataStore.data.map { preferences ->
        AppLockSettings(
            enabled = preferences[ENABLED_KEY] ?: false,
            timeout = LockTimeout.entries.firstOrNull { it.name == preferences[TIMEOUT_KEY] }
                ?: LockTimeout.ONE_MINUTE,
        )
    }.distinctUntilChanged()

    override suspend fun save(settings: AppLockSettings) {
        dataStore.edit {
            it[ENABLED_KEY] = settings.enabled
            it[TIMEOUT_KEY] = settings.timeout.name
        }
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("app_lock_enabled")
        val TIMEOUT_KEY = stringPreferencesKey("app_lock_timeout")
    }
}
