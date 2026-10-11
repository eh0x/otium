package app.otium.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.auth.ConsentResult
import app.otium.auth.ConsentStep
import app.otium.auth.DriveConsent
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BackupSettings
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.RestorePreview
import app.otium.domain.port.BackupPreferences
import app.otium.domain.usecase.ConfigureAutoBackup
import app.otium.domain.usecase.LibraryRestore
import app.otium.domain.usecase.RunBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * State holder for the backup screen. It runs one thing at a time: a tap while something runs is
 * ignored. A restore is two steps, [onRestore] to download and check, [onRestoreConfirmed] once
 * the user has seen what it holds; a downloaded file that is never confirmed is deleted when the
 * user cancels or leaves. An encrypted backup whose key this phone lacks asks for its passphrase
 * in between, and [onRestore] is called again with it. The encryption setting itself belongs to
 * [EncryptionViewModel].
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val consent: DriveConsent,
    private val preferences: BackupPreferences,
    private val runBackup: RunBackup,
    private val restore: LibraryRestore,
    private val configureAutoBackup: ConfigureAutoBackup,
) : ViewModel() {

    private data class Transient(
        val busy: BackupBusy = BackupBusy.NONE,
        val preview: RestorePreview? = null,
        val unlock: PassphrasePrompt.Unlock? = null,
        val consent: ConsentStep.AskUser? = null,
        val message: BackupMessage? = null,
        val error: AppError? = null,
    ) {
        /** After preparing the restore of [slot]: show it, ask for the passphrase, or fail. */
        fun prepared(slot: BackupSlot, result: Try<RestorePreview>): Transient = when (result) {
            is Try.Success -> copy(preview = result.value, unlock = null)

            is Try.Failure -> when (result.error) {
                AppError.PassphraseRequired -> copy(unlock = PassphrasePrompt.Unlock(slot))
                AppError.WrongPassphrase -> copy(unlock = PassphrasePrompt.Unlock(slot, true))
                else -> copy(unlock = null, error = result.error)
            }
        }
    }

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<BackupUiState> = combine(
        consent.state,
        preferences.settings,
        preferences.lastBackup,
        transient,
    ) { auth, settings, last, now ->
        BackupUiState(
            auth = auth,
            settings = settings,
            lastBackup = last,
            busy = now.busy,
            restorePreview = now.preview,
            unlock = now.unlock,
            consent = now.consent,
            message = now.message,
            error = now.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BackupUiState(),
    )

    fun onConnect() {
        whenIdle(BackupBusy.CONNECTING) {
            when (val step = consent.begin()) {
                is Try.Failure -> fail(step.error)

                is Try.Success -> when (val next = step.value) {
                    ConsentStep.Granted -> Unit
                    is ConsentStep.AskUser -> transient.update { it.copy(consent = next) }
                }
            }
        }
    }

    /** The screen has launched Google's consent screen. */
    fun onConsentLaunched() = transient.update { it.copy(consent = null) }

    fun onConsentResult(result: ConsentResult) {
        viewModelScope.launch {
            val outcome = consent.finish(result)
            if (outcome is Try.Failure) fail(outcome.error)
        }
    }

    /** Signs out and stops scheduled backups, which could no longer run. */
    fun onDisconnect() {
        viewModelScope.launch {
            consent.signOut()
            configureAutoBackup(preferences.settings.first().copy(autoBackup = false))
        }
    }

    fun onBackUpNow() {
        whenIdle(BackupBusy.BACKING_UP) {
            when (val result = runBackup()) {
                is Try.Success -> transient.update { it.copy(message = BackupMessage.BACKED_UP) }
                is Try.Failure -> fail(result.error)
            }
        }
    }

    /**
     * Downloads and checks the backup in [slot]. An encrypted one this phone cannot open asks for
     * its [passphrase], and the call is repeated with it. The array is cleared once used.
     */
    fun onRestore(slot: BackupSlot, passphrase: CharArray? = null) {
        val started = whenIdle(BackupBusy.PREPARING_RESTORE) {
            try {
                val result = restore.prepare(slot, passphrase)
                transient.update { it.prepared(slot, result) }
            } finally {
                passphrase?.fill(Char.MIN_VALUE)
            }
        }
        if (!started) passphrase?.fill(Char.MIN_VALUE)
    }

    fun onRestoreConfirmed() {
        if (transient.value.preview == null) return
        whenIdle(BackupBusy.RESTORING) {
            transient.update { it.copy(preview = null) }
            when (val result = restore.confirm()) {
                is Try.Success -> transient.update { it.copy(message = BackupMessage.RESTORED) }
                is Try.Failure -> fail(result.error)
            }
        }
    }

    /** Cancels the restore: drops a downloaded backup, or stops asking for its passphrase. */
    fun onRestoreDismissed() {
        restore.cancel()
        transient.update { it.copy(preview = null, unlock = null) }
    }

    fun onSettingsChanged(settings: BackupSettings) {
        viewModelScope.launch { configureAutoBackup(settings) }
    }

    fun onMessageShown() = transient.update { it.copy(message = null) }

    fun onErrorShown() = transient.update { it.copy(error = null) }

    override fun onCleared() {
        restore.cancel()
    }

    private fun fail(error: AppError) = transient.update { it.copy(error = error) }

    /**
     * Runs [block] as [busy] unless something is already running. True when [block] was started.
     */
    private fun whenIdle(busy: BackupBusy, block: suspend () -> Unit): Boolean {
        if (transient.value.busy != BackupBusy.NONE) return false
        transient.update { it.copy(busy = busy) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                transient.update { it.copy(busy = BackupBusy.NONE) }
            }
        }
        return true
    }
}
