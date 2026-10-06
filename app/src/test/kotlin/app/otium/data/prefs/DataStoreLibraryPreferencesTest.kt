package app.otium.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otium.domain.model.LibraryViewMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreLibraryPreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun store(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    @Test
    fun `the shelf is the default view`() = runTest {
        val prefs = DataStoreLibraryPreferences(store(folder.newFile("a.preferences_pb")))

        assertEquals(LibraryViewMode.SHELF, prefs.viewMode.first())
    }

    @Test
    fun `a chosen view mode is stored`() = runTest {
        val prefs = DataStoreLibraryPreferences(store(folder.newFile("b.preferences_pb")))

        prefs.setViewMode(LibraryViewMode.LIST)

        assertEquals(LibraryViewMode.LIST, prefs.viewMode.first())
    }

    @Test
    fun `the choice survives a new instance reading the same file`() = runTest {
        val file = File(folder.root, "c.preferences_pb")
        DataStoreLibraryPreferences(store(file)).setViewMode(LibraryViewMode.LIST)
        scope.cancel()

        val reopened = DataStoreLibraryPreferences(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                produceFile = { file },
            ),
        )

        assertEquals(LibraryViewMode.LIST, reopened.viewMode.first())
    }

    @Test
    fun `an unknown stored value falls back to the shelf`() = runTest {
        val dataStore = store(folder.newFile("d.preferences_pb"))
        dataStore.edit { it[stringPreferencesKey("library_view_mode")] = "HOLOGRAM" }

        assertEquals(LibraryViewMode.SHELF, DataStoreLibraryPreferences(dataStore).viewMode.first())
    }
}
