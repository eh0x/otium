package app.otium.domain.fakes

import app.otium.domain.model.AppLockSettings
import app.otium.domain.port.AppLockPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The app lock choice held in memory. */
class FakeAppLockPreferences(initial: AppLockSettings = AppLockSettings()) : AppLockPreferences {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppLockSettings> = state

    override suspend fun save(settings: AppLockSettings) {
        state.value = settings
    }
}
