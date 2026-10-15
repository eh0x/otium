package app.otium.ui.applock

import androidx.fragment.app.FragmentActivity
import app.otium.applock.AuthPrompt
import app.otium.applock.AuthResult
import app.otium.applock.FakeDeviceAuthenticator
import app.otium.applock.LockAvailability
import app.otium.domain.fakes.FakeAppLockPreferences
import app.otium.domain.model.AppLockSettings
import app.otium.domain.model.LockTimeout
import app.otium.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppLockViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val preferences = FakeAppLockPreferences()
    private val authenticator = FakeDeviceAuthenticator()
    private val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    private val prompt = AuthPrompt("Turn on app lock", "Use your face")

    private fun viewModel() = AppLockViewModel(preferences, authenticator)

    @Test
    fun `turning the lock on asks the owner first`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onToggle(activity, on = true, prompt)
        advanceUntilIdle()

        assertEquals(listOf(prompt), authenticator.prompts)
        assertTrue(preferences.settings.value.enabled)
        assertTrue(vm.uiState.value.enabled)
    }

    @Test
    fun `a cancelled prompt changes nothing`() = runTest {
        authenticator.result = AuthResult.Cancelled
        val vm = viewModel()

        vm.onToggle(activity, on = true, prompt)
        advanceUntilIdle()

        assertFalse(preferences.settings.value.enabled)
    }

    @Test
    fun `turning the lock off also asks, so a borrower cannot switch it off`() = runTest {
        preferences.save(AppLockSettings(enabled = true))
        authenticator.result = AuthResult.Cancelled
        val vm = viewModel()

        vm.onToggle(activity, on = false, prompt)
        advanceUntilIdle()

        assertEquals(1, authenticator.prompts.size)
        assertTrue(preferences.settings.value.enabled)
    }

    @Test
    fun `a refusal from the system is shown in its own words`() = runTest {
        authenticator.result = AuthResult.Failed("Too many attempts")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onToggle(activity, on = true, prompt)
        advanceUntilIdle()

        assertEquals("Too many attempts", vm.uiState.value.failure)
        assertFalse(preferences.settings.value.enabled)
    }

    @Test
    fun `without a screen lock the lock cannot be turned on and nothing is asked`() = runTest {
        authenticator.availability = LockAvailability.NO_SCREEN_LOCK
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onToggle(activity, on = true, prompt)
        advanceUntilIdle()

        assertTrue(authenticator.prompts.isEmpty())
        assertFalse(preferences.settings.value.enabled)
        assertEquals(LockAvailability.NO_SCREEN_LOCK, vm.uiState.value.availability)
    }

    @Test
    fun `without a screen lock the lock can be turned off without asking`() = runTest {
        preferences.save(AppLockSettings(enabled = true))
        authenticator.availability = LockAvailability.NO_SCREEN_LOCK
        val vm = viewModel()

        vm.onToggle(activity, on = false, prompt)
        advanceUntilIdle()

        assertTrue(authenticator.prompts.isEmpty())
        assertFalse(preferences.settings.value.enabled)
    }

    @Test
    fun `a screen lock set meanwhile is noticed on refresh`() = runTest {
        authenticator.availability = LockAvailability.NO_SCREEN_LOCK
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        authenticator.availability = LockAvailability.AVAILABLE
        vm.refresh()
        advanceUntilIdle()

        assertEquals(LockAvailability.AVAILABLE, vm.uiState.value.availability)
        assertNull(vm.uiState.value.failure)
    }

    @Test
    fun `the timeout can be changed`() = runTest {
        preferences.save(AppLockSettings(enabled = true))
        val vm = viewModel()

        vm.onTimeout(LockTimeout.FIVE_MINUTES)
        advanceUntilIdle()

        assertEquals(LockTimeout.FIVE_MINUTES, preferences.settings.value.timeout)
        assertTrue(preferences.settings.value.enabled)
    }
}
