package app.otium.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MovePreferencesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    /** The two stores as one launch of the app opens them. */
    private inner class Launch {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            .also(scopes::add)

        val library: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(folder.root, "library.preferences_pb")
        }

        val backupState: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            migrations = listOf(MovePreferences(library, ::isBackupState)),
        ) { File(folder.root, "backup_state.preferences_pb") }

        fun end() = scope.cancel()
    }

    @Test
    fun `the Drive, backup and app lock keys move out of the library settings`() = runTest {
        val launch = Launch()
        launch.library.edit {
            it[VIEW_MODE] = "SHELF"
            it[CONNECTED] = true
            it[ACCOUNT] = "reader@example.com"
            it[FAILED_RUNS] = 2
            it[APP_LOCK_ENABLED] = true
        }

        val moved = launch.backupState.data.first()

        assertEquals(true, moved[CONNECTED])
        assertEquals("reader@example.com", moved[ACCOUNT])
        assertEquals(2, moved[FAILED_RUNS])
        assertEquals(true, moved[APP_LOCK_ENABLED])
        assertNull(moved[VIEW_MODE])
        assertEquals(mapOf(Pair(VIEW_MODE, "SHELF")), launch.library.data.first().asMap())
    }

    @Test
    fun `a value already in the new file wins, and the stale one is dropped`() = runTest {
        val first = Launch()
        first.backupState.edit { it[ACCOUNT] = "new@example.com" }
        first.library.edit { it[ACCOUNT] = "old@example.com" }
        first.end()

        val next = Launch()

        assertEquals("new@example.com", next.backupState.data.first()[ACCOUNT])
        assertNull(next.library.data.first()[ACCOUNT])
    }

    @Test
    fun `nothing to move leaves the library settings as they are`() = runTest {
        val launch = Launch()
        launch.library.edit { it[VIEW_MODE] = "LIST" }

        assertEquals(emptyMap<Preferences.Key<*>, Any>(), launch.backupState.data.first().asMap())
        assertEquals("LIST", launch.library.data.first()[VIEW_MODE])
    }

    private companion object {
        val VIEW_MODE = stringPreferencesKey("library_view_mode")
        val CONNECTED = booleanPreferencesKey("drive_connected")
        val ACCOUNT = stringPreferencesKey("drive_account")
        val FAILED_RUNS = intPreferencesKey("backup_failed_runs")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }
}
