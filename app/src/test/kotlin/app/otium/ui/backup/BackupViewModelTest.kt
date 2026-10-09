package app.otium.ui.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.otium.auth.ConsentResult
import app.otium.auth.ConsentStep
import app.otium.auth.DriveConsent
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBackupArchiver
import app.otium.domain.fakes.FakeBackupEncryption
import app.otium.domain.fakes.FakeBackupPreferences
import app.otium.domain.fakes.FakeBackupScheduler
import app.otium.domain.fakes.FakeDriveBackupClient
import app.otium.domain.fakes.FakeLibraryRestorer
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupSettings
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.DriveAuthState
import app.otium.domain.usecase.BackupLock
import app.otium.domain.usecase.ConfigureAutoBackup
import app.otium.domain.usecase.PrepareRestore
import app.otium.domain.usecase.RestoreBackup
import app.otium.domain.usecase.RunBackup
import app.otium.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric because Google's consent step carries a [PendingIntent]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeDriveConsent : DriveConsent {
        val auth = MutableStateFlow<DriveAuthState>(DriveAuthState.SignedIn("reader@example.org"))
        override val state: Flow<DriveAuthState> = auth

        var step: Try<ConsentStep> = Try.Success(ConsentStep.Granted)
        var finishResult: Try<Unit> = Try.Success(Unit)
        val finished = mutableListOf<ConsentResult>()
        var signOuts = 0

        override suspend fun signOut() {
            signOuts++
            auth.value = DriveAuthState.SignedOut
        }

        override suspend fun begin(): Try<ConsentStep> = step

        override suspend fun finish(result: ConsentResult): Try<Unit> {
            finished += result
            return finishResult
        }
    }

    private val consent = FakeDriveConsent()
    private val drive = FakeDriveBackupClient()
    private val archiver = FakeBackupArchiver()
    private val prefs = FakeBackupPreferences()
    private val restorer = FakeLibraryRestorer()
    private val scheduler = FakeBackupScheduler()
    private val encryption = FakeBackupEncryption()
    private var lookupsPlanned = 0

    private fun viewModel(): BackupViewModel {
        val lock = BackupLock()
        return BackupViewModel(
            consent = consent,
            preferences = prefs,
            runBackup = RunBackup(drive, archiver, encryption, prefs, lock),
            prepareRestore = PrepareRestore(drive, archiver, encryption, appSchemaVersion = 1),
            restoreBackup = RestoreBackup(restorer, { lookupsPlanned++ }, encryption, lock),
            configureAutoBackup = ConfigureAutoBackup(prefs, scheduler),
        )
    }

    private fun TestScope.screenOpen(vm: BackupViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext<Context>(),
        0,
        Intent("consent"),
        PendingIntent.FLAG_IMMUTABLE,
    )

    // --- Showing the state ---

    @Test
    fun `the state shows the account, the settings and the last backup`() = runTest {
        prefs.saveSettings(BackupSettings(autoBackup = true))
        prefs.recordBackup(backupInfo(bookCount = 9))
        val vm = viewModel()
        screenOpen(vm)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(DriveAuthState.SignedIn("reader@example.org"), state.auth)
        assertTrue(state.settings.autoBackup)
        assertEquals(9, state.lastBackup?.bookCount)
        assertFalse(state.isBusy)
    }

    // --- Backing up ---

    @Test
    fun `backing up reports success and remembers the backup`() = runTest {
        archiver.info = backupInfo(bookCount = 4)
        val vm = viewModel()
        screenOpen(vm)

        vm.onBackUpNow()
        runCurrent()
        assertEquals(BackupBusy.BACKING_UP, vm.uiState.value.busy)
        advanceUntilIdle()

        assertEquals(BackupMessage.BACKED_UP, vm.uiState.value.message)
        assertEquals(BackupBusy.NONE, vm.uiState.value.busy)
        assertEquals(4, vm.uiState.value.lastBackup?.bookCount)
        vm.onMessageShown()
        advanceUntilIdle()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `a failed backup shows its error until dismissed`() = runTest {
        drive.failure = AppError.InsufficientDriveSpace
        val vm = viewModel()
        screenOpen(vm)

        vm.onBackUpNow()
        advanceUntilIdle()

        assertEquals(AppError.InsufficientDriveSpace, vm.uiState.value.error)
        assertNull(vm.uiState.value.message)
        vm.onErrorShown()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `a second tap while a backup runs is ignored`() = runTest {
        val vm = viewModel()
        screenOpen(vm)

        vm.onBackUpNow()
        vm.onBackUpNow()
        advanceUntilIdle()

        assertEquals(1, drive.uploads)
    }

    // --- Restoring ---

    @Test
    fun `a restore shows what the backup holds and changes nothing until confirmed`() = runTest {
        val info = backupInfo(bookCount = 12, createdAt = 5_000L)
        drive.put(BackupSlot.LATEST, info)
        val vm = viewModel()
        screenOpen(vm)

        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()

        assertEquals(info, vm.uiState.value.restorePreview!!.info)
        assertNull(restorer.restored)
    }

    @Test
    fun `confirming replaces the library and reports it`() = runTest {
        val info = backupInfo(bookCount = 12)
        drive.put(BackupSlot.LATEST, info)
        val vm = viewModel()
        screenOpen(vm)
        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()
        val file = vm.uiState.value.restorePreview!!.file

        vm.onRestoreConfirmed()
        advanceUntilIdle()

        assertEquals(info, restorer.restored)
        assertEquals(BackupMessage.RESTORED, vm.uiState.value.message)
        assertNull(vm.uiState.value.restorePreview)
        assertFalse(file.exists())
        assertEquals(1, lookupsPlanned)
    }

    @Test
    fun `cancelling a restore discards the download`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo())
        val vm = viewModel()
        screenOpen(vm)
        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()
        val file = vm.uiState.value.restorePreview!!.file

        vm.onRestoreDismissed()
        advanceUntilIdle()

        assertNull(vm.uiState.value.restorePreview)
        assertFalse(file.exists())
        assertNull(restorer.restored)
    }

    @Test
    fun `a failed restore reports the error and discards the download`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo())
        restorer.failure = AppError.Storage("disk full")
        val vm = viewModel()
        screenOpen(vm)
        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()
        val file = vm.uiState.value.restorePreview!!.file

        vm.onRestoreConfirmed()
        advanceUntilIdle()

        assertEquals(AppError.Storage("disk full"), vm.uiState.value.error)
        assertNull(vm.uiState.value.message)
        assertFalse(file.exists())
    }

    @Test
    fun `a backup from a newer version is refused before anything is shown`() = runTest {
        drive.put(BackupSlot.LATEST, backupInfo(schemaVersion = 2))
        val vm = viewModel()
        screenOpen(vm)

        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()

        assertEquals(AppError.IncompatibleBackup(2, 1), vm.uiState.value.error)
        assertNull(vm.uiState.value.restorePreview)
    }

    @Test
    fun `there is nothing to restore before the first backup`() = runTest {
        val vm = viewModel()
        screenOpen(vm)

        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()

        assertEquals(AppError.NotFound, vm.uiState.value.error)
    }

    // --- Connecting ---

    @Test
    fun `connecting that needs Google's screen asks the screen to show it once`() = runTest {
        val step = ConsentStep.AskUser(pendingIntent())
        consent.step = Try.Success(step)
        val vm = viewModel()
        screenOpen(vm)

        vm.onConnect()
        advanceUntilIdle()

        assertEquals(step, vm.uiState.value.consent)
        vm.onConsentLaunched()
        advanceUntilIdle()
        assertNull(vm.uiState.value.consent)
    }

    @Test
    fun `connecting that fails shows the error`() = runTest {
        consent.step = Try.Failure(AppError.Offline)
        val vm = viewModel()
        screenOpen(vm)

        vm.onConnect()
        advanceUntilIdle()

        assertEquals(AppError.Offline, vm.uiState.value.error)
        assertEquals(BackupBusy.NONE, vm.uiState.value.busy)
    }

    @Test
    fun `what Google's screen returns is recorded, and a failure is shown`() = runTest {
        val vm = viewModel()
        screenOpen(vm)
        val result = ConsentResult(Intent("granted"))

        vm.onConsentResult(result)
        advanceUntilIdle()
        assertEquals(listOf(result), consent.finished)
        assertNull(vm.uiState.value.error)

        consent.finishResult = Try.Failure(AppError.AuthExpired)
        vm.onConsentResult(result)
        advanceUntilIdle()
        assertEquals(AppError.AuthExpired, vm.uiState.value.error)
    }

    @Test
    fun `disconnecting signs out and stops scheduled backups`() = runTest {
        val on = BackupSettings(autoBackup = true)
        prefs.saveSettings(on)
        scheduler.schedule(on)
        val vm = viewModel()
        screenOpen(vm)

        vm.onDisconnect()
        advanceUntilIdle()

        assertEquals(1, consent.signOuts)
        assertFalse(prefs.currentSettings.autoBackup)
        assertNull(scheduler.scheduled)
        assertEquals(DriveAuthState.SignedOut, vm.uiState.value.auth)
    }

    // --- Encrypted backups ---

    private val plain = backupInfo(bookCount = 7)

    private fun putEncrypted() = drive.put(
        BackupSlot.LATEST,
        plain.copy(keyId = FakeBackupEncryption.keyIdOf("correct horse")),
        content = FakeBackupEncryption.encryptedContent(plain, "correct horse"),
    )

    @Test
    fun `an encrypted backup asks for its passphrase instead of failing`() = runTest {
        putEncrypted()
        val vm = viewModel()
        screenOpen(vm)

        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()

        assertEquals(PassphrasePrompt.Unlock(BackupSlot.LATEST), vm.uiState.value.unlock)
        assertNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.restorePreview)
    }

    @Test
    fun `the right passphrase shows the backup and clears the typed text`() = runTest {
        putEncrypted()
        val vm = viewModel()
        screenOpen(vm)
        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()
        val typed = "correct horse".toCharArray()

        vm.onRestore(BackupSlot.LATEST, typed)
        advanceUntilIdle()

        assertNull(vm.uiState.value.unlock)
        assertEquals(7, vm.uiState.value.restorePreview!!.info.bookCount)
        assertTrue(typed.all { it == Char.MIN_VALUE })
    }

    @Test
    fun `a wrong passphrase asks again and says it was wrong`() = runTest {
        putEncrypted()
        val vm = viewModel()
        screenOpen(vm)

        vm.onRestore(BackupSlot.LATEST, "wrong".toCharArray())
        advanceUntilIdle()

        assertEquals(
            PassphrasePrompt.Unlock(BackupSlot.LATEST, wrong = true),
            vm.uiState.value.unlock,
        )
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `cancelling the passphrase prompt ends the restore`() = runTest {
        putEncrypted()
        val vm = viewModel()
        screenOpen(vm)
        vm.onRestore(BackupSlot.LATEST)
        advanceUntilIdle()

        vm.onRestoreDismissed()
        advanceUntilIdle()

        assertNull(vm.uiState.value.unlock)
    }

    @Test
    fun `a passphrase sent while busy is cleared and ignored`() = runTest {
        val vm = viewModel()
        screenOpen(vm)
        vm.onBackUpNow() // Still archiving: the fake takes a moment.
        val typed = "correct horse".toCharArray()

        vm.onRestore(BackupSlot.LATEST, typed)
        advanceUntilIdle()

        assertTrue(typed.all { it == Char.MIN_VALUE })
        assertEquals(0, drive.downloads)
    }

    // --- Automatic backups ---

    @Test
    fun `choosing automatic backups saves the choice and plans them`() = runTest {
        val vm = viewModel()
        screenOpen(vm)
        val chosen = BackupSettings(autoBackup = true, wifiOnly = false)

        vm.onSettingsChanged(chosen)
        advanceUntilIdle()

        assertEquals(chosen, prefs.currentSettings)
        assertEquals(chosen, scheduler.scheduled)
        assertEquals(chosen, vm.uiState.value.settings)
    }
}
