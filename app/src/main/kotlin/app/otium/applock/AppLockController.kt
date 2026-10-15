package app.otium.applock

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.otium.domain.model.AppLockSettings
import app.otium.domain.port.AppLockPreferences
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.usecase.AppLockPolicy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the library may be shown. */
enum class LockState {
    /** The app lock choice is still being read; nothing is shown yet. */
    CHECKING,
    LOCKED,
    UNLOCKED,
}

/**
 * Locks the app for the whole process, following [AppLockPolicy]: on start, and on coming back
 * after the timeout. It observes the process lifecycle, so moving between Otium's own screens, or
 * to the file picker and back within the timeout, does not count as leaving. Times are taken from
 * the clock since boot, which the user cannot set.
 *
 * Turning the lock on in settings does not lock straight away (the owner has just confirmed);
 * turning it off unlocks.
 */
@Singleton
class AppLockController internal constructor(
    private val preferences: AppLockPreferences,
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long,
) : DefaultLifecycleObserver {

    @Inject
    internal constructor(preferences: AppLockPreferences, dispatchers: DispatcherProvider) : this(
        preferences,
        CoroutineScope(SupervisorJob() + dispatchers.main),
        SystemClock::elapsedRealtime,
    )

    private val lockState = MutableStateFlow(LockState.CHECKING)
    val state: StateFlow<LockState> = lockState.asStateFlow()

    private val prompts = MutableStateFlow(0)

    /**
     * Grows each time the lock screen should open the system prompt by itself: when the app
     * locks, and when it comes back while still locked. A prompt the owner cancels is not opened
     * again until then; they can press Unlock.
     */
    val promptRequests: StateFlow<Int> = prompts.asStateFlow()

    private val turnedOff = MutableStateFlow(false)

    /** True once the lock turned itself off because the phone lost its screen lock. */
    val turnedOffNotice: StateFlow<Boolean> = turnedOff.asStateFlow()

    private var settings: AppLockSettings? = null
    private var backgroundedAt: Long? = null

    init {
        scope.launch {
            preferences.settings.collect { new ->
                val starting = settings == null
                settings = new
                when {
                    !new.enabled -> lockState.value = LockState.UNLOCKED
                    starting && AppLockPolicy.shouldLock(new, null, elapsedRealtime()) -> lock()
                    starting -> lockState.value = LockState.UNLOCKED
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val current = settings ?: return
        if (lockState.value == LockState.LOCKED) {
            prompts.update { it + 1 }
            return
        }
        val leftAt = backgroundedAt ?: return
        if (AppLockPolicy.shouldLock(current, leftAt, elapsedRealtime())) lock()
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = elapsedRealtime()
    }

    /** The owner confirmed who they are. */
    fun unlocked() {
        lockState.value = LockState.UNLOCKED
    }

    /**
     * The phone no longer has a screen lock, so nobody can confirm anything: the lock turns
     * itself off rather than shut the owner out, and says so once.
     */
    fun screenLockGone() {
        settings?.let { current ->
            scope.launch { preferences.save(current.copy(enabled = false)) }
        }
        turnedOff.value = true
        lockState.value = LockState.UNLOCKED
    }

    fun noticeShown() {
        turnedOff.value = false
    }

    private fun lock() {
        lockState.value = LockState.LOCKED
        prompts.update { it + 1 }
    }
}
