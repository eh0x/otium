package app.otium.domain.port

import app.otium.domain.model.AppLockSettings
import kotlinx.coroutines.flow.Flow

/** Where the app lock choice is kept on this device. */
interface AppLockPreferences {
    val settings: Flow<AppLockSettings>

    suspend fun save(settings: AppLockSettings)
}
