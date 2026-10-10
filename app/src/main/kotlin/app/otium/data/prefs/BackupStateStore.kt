package app.otium.data.prefs

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Qualifier
import kotlinx.coroutines.flow.first

/**
 * The preferences file for the Drive connection and backup bookkeeping: whether Drive is
 * connected, the account's email, the backup choices, the last backup and the failure count. It
 * is kept apart from the library settings so that whatever moves those (such as Android's device
 * transfer) never carries the account or a grant the new phone does not have.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackupStateStore

/** True for the keys that live in the [BackupStateStore] file. */
internal fun isBackupState(key: Preferences.Key<*>): Boolean =
    key.name.startsWith("drive_") || key.name.startsWith("backup_")

/**
 * Moves the keys [belongs] accepts out of [source] into the store this migration runs on. Values
 * already in the target win, and the keys leave [source] only once the target holds them.
 */
internal class MovePreferences(
    private val source: DataStore<Preferences>,
    private val belongs: (Preferences.Key<*>) -> Boolean,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        source.data.first().asMap().keys.any(belongs)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val target = currentData.toMutablePreferences()
        source.data.first().asMap()
            .filterKeys { belongs(it) && it !in currentData.asMap() }
            .forEach { (key, value) -> target.putUnchecked(key, value) }
        return target.toPreferences()
    }

    override suspend fun cleanUp() {
        source.edit { preferences ->
            preferences.asMap().keys.filter(belongs).forEach { preferences.remove(it) }
        }
    }

    // The value was stored under this same key, so it has the key's type.
    @Suppress("UNCHECKED_CAST")
    private fun MutablePreferences.putUnchecked(key: Preferences.Key<*>, value: Any) {
        set(key as Preferences.Key<Any>, value)
    }
}
