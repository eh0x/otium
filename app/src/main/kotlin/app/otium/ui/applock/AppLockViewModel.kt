package app.otium.ui.applock

import androidx.compose.runtime.Immutable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.applock.AuthPrompt
import app.otium.applock.AuthResult
import app.otium.applock.DeviceAuthenticator
import app.otium.applock.LockAvailability
import app.otium.domain.model.LockTimeout
import app.otium.domain.port.AppLockPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

@Immutable
data class AppLockUiState(
    val enabled: Boolean = false,
    val timeout: LockTimeout = LockTimeout.ONE_MINUTE,
    val availability: LockAvailability = LockAvailability.AVAILABLE,
    /** Why the system refused the last attempt, in its own words (for example too many tries). */
    val failure: String? = null,
)

/** What the app lock setting can ask for. Defaults do nothing. */
@Immutable
data class AppLockActions(
    /** Turn the lock on (true) or off; the owner confirms first. */
    val onToggle: (Boolean) -> Unit = {},
    val onTimeout: (LockTimeout) -> Unit = {},
    /** Open Android's security settings, to set a screen lock. */
    val onOpenSecuritySettings: () -> Unit = {},
)

/**
 * State holder for the app lock setting. Turning the lock on or off asks the owner to confirm
 * first: on, so it is only on where the phone can ask; off, so whoever borrows the phone cannot
 * simply switch it off. Without a screen lock the lock cannot be turned on, and can be turned off
 * without asking, since nothing could be asked.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val preferences: AppLockPreferences,
    private val authenticator: DeviceAuthenticator,
) : ViewModel() {

    private val availability = MutableStateFlow(authenticator.availability())
    private val failure = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppLockUiState> = combine(
        preferences.settings,
        availability,
        failure,
    ) { settings, available, failed ->
        AppLockUiState(settings.enabled, settings.timeout, available, failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppLockUiState())

    /** Checks again whether the phone has a screen lock, for example back from Android settings. */
    fun refresh() {
        availability.value = authenticator.availability()
    }

    /** Asks the owner to confirm over [activity], then turns the lock [on] or off. */
    fun onToggle(activity: FragmentActivity, on: Boolean, prompt: AuthPrompt) {
        refresh()
        failure.value = null
        if (availability.value != LockAvailability.AVAILABLE) {
            if (!on) save(enabled = false)
            return
        }
        authenticator.authenticate(activity, prompt) { result ->
            when (result) {
                AuthResult.Success -> save(enabled = on)
                AuthResult.NoScreenLock -> availability.value = LockAvailability.NO_SCREEN_LOCK
                is AuthResult.Failed -> failure.value = result.message
                AuthResult.Cancelled -> Unit
            }
        }
    }

    fun onTimeout(timeout: LockTimeout) {
        viewModelScope.launch {
            preferences.save(preferences.settings.first().copy(timeout = timeout))
        }
    }

    private fun save(enabled: Boolean) {
        viewModelScope.launch {
            preferences.save(preferences.settings.first().copy(enabled = enabled))
        }
    }
}
