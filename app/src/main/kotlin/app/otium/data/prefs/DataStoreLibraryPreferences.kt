package app.otium.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.port.LibraryPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores the library presentation choices in Jetpack DataStore. */
class DataStoreLibraryPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LibraryPreferences {

    override val viewMode: Flow<LibraryViewMode> = dataStore.data.map { preferences ->
        LibraryViewMode.entries.firstOrNull { it.name == preferences[VIEW_MODE_KEY] }
            ?: LibraryViewMode.SHELF
    }

    override suspend fun setViewMode(mode: LibraryViewMode) {
        dataStore.edit { it[VIEW_MODE_KEY] = mode.name }
    }

    private companion object {
        val VIEW_MODE_KEY = stringPreferencesKey("library_view_mode")
    }
}
