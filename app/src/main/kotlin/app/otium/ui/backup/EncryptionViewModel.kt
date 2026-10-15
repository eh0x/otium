package app.otium.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.DriveAuthState
import app.otium.domain.port.BackupEncryption
import app.otium.domain.port.BackupPreferences
import app.otium.domain.port.DriveAuth
import app.otium.domain.usecase.SetBackupPassphrase
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
 * State holder for backup encryption on the backup screen: setting or changing the passphrase,
 * and turning encryption off after the user confirms. Passphrases arrive as character arrays and
 * are cleared as soon as they have been used, whatever the outcome.
 *
 * Encryption is the suggested default (D32): once Drive is connected without it, the passphrase
 * panel opens by itself as the recommended choice, once. "Not now" closes it for good; the
 * setting stays where it always was.
 */
@HiltViewModel
class EncryptionViewModel @Inject constructor(
    private val encryption: BackupEncryption,
    private val setBackupPassphrase: SetBackupPassphrase,
    private val driveAuth: DriveAuth,
    private val preferences: BackupPreferences,
) : ViewModel() {

    private data class Transient(
        val working: Boolean = false,
        val prompt: PassphrasePrompt.Set? = null,
        val confirmTurnOff: Boolean = false,
        val message: BackupMessage? = null,
        val error: AppError? = null,
    ) {
        /** After trying a new passphrase for [prompt]: done, say what is wrong with it, or fail. */
        fun passphraseSet(prompt: PassphrasePrompt.Set, result: Try<Unit>): Transient =
            when (result) {
                is Try.Success -> copy(prompt = null, message = BackupMessage.ENCRYPTION_ON)

                is Try.Failure -> {
                    val problem = (result.error as? AppError.InvalidInput)?.problem()
                    if (problem != null) {
                        copy(prompt = prompt.copy(problem = problem))
                    } else {
                        copy(prompt = null, error = result.error)
                    }
                }
            }
    }

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<EncryptionUiState> =
        combine(encryption.activeKeyId, transient) { keyId, now ->
            EncryptionUiState(
                isOn = keyId != null,
                isWorking = now.working,
                prompt = now.prompt,
                confirmTurnOff = now.confirmTurnOff,
                message = now.message,
                error = now.error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = EncryptionUiState(),
        )

    // Declared after the state it updates: viewModelScope starts at once.
    init {
        viewModelScope.launch {
            combine(driveAuth.state, encryption.activeKeyId, preferences.encryptionSuggested) {
                    auth,
                    keyId,
                    suggested,
                ->
                auth is DriveAuthState.SignedIn && keyId == null && !suggested
            }.first { it }
            preferences.markEncryptionSuggested()
            transient.update {
                if (it.prompt != null) {
                    it
                } else {
                    it.copy(prompt = PassphrasePrompt.Set(changing = false, suggested = true))
                }
            }
        }
    }

    /** Asks for a passphrase: a first one, or a new one when encryption is already on. */
    fun onSetPassphrase() {
        viewModelScope.launch {
            val changing = encryption.activeKeyId.first() != null
            transient.update { it.copy(prompt = PassphrasePrompt.Set(changing)) }
        }
    }

    /** Clears [passphrase] and [confirmation] once used, whatever the outcome. */
    fun onPassphraseEntered(passphrase: CharArray, confirmation: CharArray) {
        val now = transient.value
        val prompt = now.prompt
        if (prompt == null || now.working) {
            passphrase.wipe()
            confirmation.wipe()
            return
        }
        transient.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val result = setBackupPassphrase(passphrase, confirmation)
                transient.update { it.passphraseSet(prompt, result) }
            } finally {
                passphrase.wipe()
                confirmation.wipe()
                transient.update { it.copy(working = false) }
            }
        }
    }

    /** Closes the passphrase prompt, unless the key is being derived right now. */
    fun onPassphraseDismissed() = transient.update {
        if (it.working) it else it.copy(prompt = null)
    }

    fun onTurnOff() = transient.update { it.copy(confirmTurnOff = true) }

    fun onTurnOffDismissed() = transient.update { it.copy(confirmTurnOff = false) }

    fun onTurnOffConfirmed() {
        transient.update { it.copy(confirmTurnOff = false) }
        viewModelScope.launch {
            encryption.disable()
            transient.update { it.copy(message = BackupMessage.ENCRYPTION_OFF) }
        }
    }

    fun onMessageShown() = transient.update { it.copy(message = null) }

    fun onErrorShown() = transient.update { it.copy(error = null) }
}

private fun AppError.InvalidInput.problem(): PassphraseProblem? = when (field) {
    SetBackupPassphrase.FIELD_PASSPHRASE -> PassphraseProblem.TOO_SHORT
    SetBackupPassphrase.FIELD_CONFIRMATION -> PassphraseProblem.MISMATCH
    else -> null
}

private fun CharArray.wipe() = fill(Char.MIN_VALUE)
