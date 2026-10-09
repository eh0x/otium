package app.otium.ui.backup

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBackupEncryption
import app.otium.domain.port.BackupEncryption
import app.otium.domain.usecase.SetBackupPassphrase
import app.otium.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EncryptionViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val encryption = FakeBackupEncryption()

    private fun viewModel(with: BackupEncryption = encryption) =
        EncryptionViewModel(with, SetBackupPassphrase(with))

    private fun TestScope.screenOpen(vm: EncryptionViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private fun EncryptionViewModel.enter(passphrase: String, confirmation: String = passphrase) =
        onPassphraseEntered(passphrase.toCharArray(), confirmation.toCharArray())

    private fun TestScope.promptOpen(vm: EncryptionViewModel) {
        screenOpen(vm)
        vm.onSetPassphrase()
        advanceUntilIdle()
    }

    @Test
    fun `the state says whether encryption is on`() = runTest {
        val vm = viewModel()
        screenOpen(vm)
        assertFalse(vm.uiState.value.isOn)

        encryption.turnOn("correct horse")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isOn)
    }

    @Test
    fun `setting a first passphrase turns encryption on and says so`() = runTest {
        val vm = viewModel()
        promptOpen(vm)
        assertEquals(PassphrasePrompt.Set(changing = false), vm.uiState.value.prompt)

        vm.enter("correct horse")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isOn)
        assertNull(vm.uiState.value.prompt)
        assertEquals(BackupMessage.ENCRYPTION_ON, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isWorking)
    }

    @Test
    fun `with encryption on, the prompt is for changing the passphrase`() = runTest {
        encryption.turnOn("correct horse")
        val vm = viewModel()

        promptOpen(vm)

        assertEquals(PassphrasePrompt.Set(changing = true), vm.uiState.value.prompt)
    }

    @Test
    fun `a short passphrase keeps the prompt open and says why`() = runTest {
        val vm = viewModel()
        promptOpen(vm)

        vm.enter("short")
        advanceUntilIdle()

        assertEquals(
            PassphrasePrompt.Set(changing = false, problem = PassphraseProblem.TOO_SHORT),
            vm.uiState.value.prompt,
        )
        assertFalse(vm.uiState.value.isOn)
    }

    @Test
    fun `a mismatched confirmation keeps the prompt open and says why`() = runTest {
        val vm = viewModel()
        promptOpen(vm)

        vm.enter("correct horse", "correct house")
        advanceUntilIdle()

        assertEquals(PassphraseProblem.MISMATCH, vm.uiState.value.prompt?.problem)
    }

    @Test
    fun `a failure to store the key closes the prompt and shows the error`() = runTest {
        val failing = object : BackupEncryption by encryption {
            override suspend fun enable(passphrase: CharArray): Try<Unit> =
                Try.Failure(AppError.Unexpected("keystore"))
        }
        val vm = viewModel(failing)
        promptOpen(vm)

        vm.enter("correct horse")
        advanceUntilIdle()

        assertNull(vm.uiState.value.prompt)
        assertEquals(AppError.Unexpected("keystore"), vm.uiState.value.error)
    }

    @Test
    fun `typed passphrases are cleared whatever the outcome`() = runTest {
        val vm = viewModel()
        promptOpen(vm)
        val passphrase = "correct horse".toCharArray()
        val confirmation = "correct house".toCharArray()

        vm.onPassphraseEntered(passphrase, confirmation)
        advanceUntilIdle()

        assertTrue(passphrase.all { it == Char.MIN_VALUE })
        assertTrue(confirmation.all { it == Char.MIN_VALUE })
    }

    @Test
    fun `a passphrase with no prompt open is cleared and ignored`() = runTest {
        val vm = viewModel()
        screenOpen(vm)
        val passphrase = "correct horse".toCharArray()

        vm.onPassphraseEntered(passphrase, passphrase.copyOf())
        advanceUntilIdle()

        assertTrue(passphrase.all { it == Char.MIN_VALUE })
        assertFalse(vm.uiState.value.isOn)
    }

    @Test
    fun `cancelling closes the prompt without a change`() = runTest {
        val vm = viewModel()
        promptOpen(vm)

        vm.onPassphraseDismissed()
        advanceUntilIdle()

        assertNull(vm.uiState.value.prompt)
        assertFalse(vm.uiState.value.isOn)
    }

    @Test
    fun `turning encryption off asks first, then does it and says so`() = runTest {
        encryption.turnOn("correct horse")
        val vm = viewModel()
        screenOpen(vm)

        vm.onTurnOff()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.confirmTurnOff)
        assertTrue(vm.uiState.value.isOn)
        vm.onTurnOffConfirmed()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.confirmTurnOff)
        assertFalse(vm.uiState.value.isOn)
        assertEquals(BackupMessage.ENCRYPTION_OFF, vm.uiState.value.message)
    }

    @Test
    fun `declining to turn it off keeps it on`() = runTest {
        encryption.turnOn("correct horse")
        val vm = viewModel()
        screenOpen(vm)

        vm.onTurnOff()
        vm.onTurnOffDismissed()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.confirmTurnOff)
        assertTrue(vm.uiState.value.isOn)
    }

    @Test
    fun `a message stays until shown`() = runTest {
        val vm = viewModel()
        promptOpen(vm)
        vm.enter("correct horse")
        advanceUntilIdle()

        vm.onMessageShown()
        advanceUntilIdle()

        assertNull(vm.uiState.value.message)
    }
}
