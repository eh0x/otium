package app.otium.applock

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import app.otium.domain.fakes.FakeAppLockPreferences
import app.otium.domain.model.AppLockSettings
import app.otium.domain.model.LockTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockControllerTest {

    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("not used")
    }
    private var now = 1_000L
    private val on = AppLockSettings(enabled = true, timeout = LockTimeout.ONE_MINUTE)
    private lateinit var preferences: FakeAppLockPreferences

    /** A controller whose work runs at once and ends with the test. */
    private fun TestScope.started(settings: AppLockSettings): AppLockController {
        preferences = FakeAppLockPreferences(settings)
        val scope = CoroutineScope(
            backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
        )
        return AppLockController(preferences, scope) { now }
    }

    @Test
    fun `with the lock off the library opens straight away`() = runTest {
        val controller = started(AppLockSettings())

        assertEquals(LockState.UNLOCKED, controller.state.value)
    }

    @Test
    fun `with the lock on every start locks and asks once`() = runTest {
        val controller = started(on)

        assertEquals(LockState.LOCKED, controller.state.value)
        assertEquals(1, controller.promptRequests.value)
    }

    @Test
    fun `unlocking shows the library`() = runTest {
        val controller = started(on)

        controller.unlocked()

        assertEquals(LockState.UNLOCKED, controller.state.value)
    }

    @Test
    fun `a short time away does not lock again`() = runTest {
        val controller = started(on)
        controller.unlocked()

        controller.onStop(owner)
        now += 59_000L
        controller.onStart(owner)

        assertEquals(LockState.UNLOCKED, controller.state.value)
    }

    @Test
    fun `coming back after the timeout locks and asks again`() = runTest {
        val controller = started(on)
        controller.unlocked()

        controller.onStop(owner)
        now += 60_000L
        controller.onStart(owner)

        assertEquals(LockState.LOCKED, controller.state.value)
        assertEquals(2, controller.promptRequests.value)
    }

    @Test
    fun `coming back while still locked asks again`() = runTest {
        val controller = started(on)

        controller.onStop(owner)
        controller.onStart(owner)

        assertEquals(LockState.LOCKED, controller.state.value)
        assertEquals(2, controller.promptRequests.value)
    }

    @Test
    fun `turning the lock on does not lock the owner who just confirmed`() = runTest {
        val controller = started(AppLockSettings())

        preferences.save(on)

        assertEquals(LockState.UNLOCKED, controller.state.value)
    }

    @Test
    fun `turning the lock off unlocks`() = runTest {
        val controller = started(on)

        preferences.save(on.copy(enabled = false))

        assertEquals(LockState.UNLOCKED, controller.state.value)
    }

    @Test
    fun `without a screen lock the lock turns itself off and says so once`() = runTest {
        val controller = started(on)

        controller.screenLockGone()

        assertEquals(LockState.UNLOCKED, controller.state.value)
        assertFalse(preferences.settings.value.enabled)
        assertTrue(controller.turnedOffNotice.value)
        controller.noticeShown()
        assertFalse(controller.turnedOffNotice.value)
    }
}
