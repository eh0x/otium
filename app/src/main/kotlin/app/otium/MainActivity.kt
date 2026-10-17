package app.otium

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.otium.applock.AppLockController
import app.otium.applock.AuthPrompt
import app.otium.applock.AuthResult
import app.otium.applock.DeviceAuthenticator
import app.otium.applock.LockAvailability
import app.otium.domain.port.AppLockPreferences
import app.otium.ui.applock.AppLockGate
import app.otium.ui.applock.AppLockGateState
import app.otium.ui.navigation.OtiumNavHost
import app.otium.ui.theme.OtiumTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The one activity. A [FragmentActivity] because the system biometric prompt is shown over one.
 * The app lock gate sits around everything; while the lock is on, Otium's screen is also left out
 * of the recent apps preview.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLock: AppLockController

    @Inject
    lateinit var authenticator: DeviceAuthenticator

    @Inject
    lateinit var appLockPreferences: AppLockPreferences

    private var unlockMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideFromRecentsWhileLocked()
        setContent {
            OtiumTheme {
                val state by appLock.state.collectAsStateWithLifecycle()
                val promptRequest by appLock.promptRequests.collectAsStateWithLifecycle()
                val turnedOff by appLock.turnedOffNotice.collectAsStateWithLifecycle()
                AppLockGate(
                    gate = AppLockGateState(state, promptRequest, turnedOff, unlockMessage),
                    onUnlock = ::unlock,
                    onNoticeShown = appLock::noticeShown,
                ) {
                    OtiumNavHost()
                }
            }
        }
    }

    private fun unlock() {
        if (authenticator.availability() == LockAvailability.NO_SCREEN_LOCK) {
            appLock.screenLockGone()
            return
        }
        unlockMessage = null
        val prompt = AuthPrompt(
            title = getString(R.string.app_lock_prompt_title),
            subtitle = getString(R.string.app_lock_prompt_subtitle),
        )
        authenticator.authenticate(this, prompt) { result ->
            when (result) {
                AuthResult.Success -> appLock.unlocked()
                AuthResult.NoScreenLock -> appLock.screenLockGone()
                is AuthResult.Failed -> unlockMessage = result.message
                AuthResult.Cancelled -> Unit
            }
        }
    }

    private fun hideFromRecentsWhileLocked() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                appLockPreferences.settings.collect { settings ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setRecentsScreenshotEnabled(!settings.enabled)
                    } else if (settings.enabled) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }
}
