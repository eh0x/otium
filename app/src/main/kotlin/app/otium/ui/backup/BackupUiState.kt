package app.otium.ui.backup

import androidx.compose.runtime.Immutable
import app.otium.auth.ConsentStep
import app.otium.domain.error.AppError
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSettings
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.DriveAuthState
import app.otium.domain.model.RestorePreview

/** What the screen is waiting on. Only one thing runs at a time. */
enum class BackupBusy { NONE, CONNECTING, BACKING_UP, PREPARING_RESTORE, RESTORING }

/** Something that went right, shown briefly. */
enum class BackupMessage { BACKED_UP, RESTORED, ENCRYPTION_ON, ENCRYPTION_OFF }

/** Why a new passphrase was not accepted. */
enum class PassphraseProblem { TOO_SHORT, MISMATCH }

/** A passphrase the screen is asking for. */
sealed interface PassphrasePrompt {
    /** Turn encryption on, or change the passphrase when [changing]. */
    data class Set(val changing: Boolean, val problem: PassphraseProblem? = null) :
        PassphrasePrompt

    /** Unlock the encrypted backup in [slot] to restore it; [wrong] after a failed try. */
    data class Unlock(val slot: BackupSlot, val wrong: Boolean = false) : PassphrasePrompt
}

/**
 * Everything the backup screen shows. Outcomes are state, not one-off events: [message] and
 * [error] stay until the screen has shown them, [consent] until it has launched Google's screen,
 * [restorePreview] until the user confirms or cancels the restore, and [unlock] until the backup
 * opens or the user cancels.
 */
@Immutable
data class BackupUiState(
    val auth: DriveAuthState = DriveAuthState.SignedOut,
    val settings: BackupSettings = BackupSettings(),
    val lastBackup: BackupInfo? = null,
    val busy: BackupBusy = BackupBusy.NONE,
    val restorePreview: RestorePreview? = null,
    val unlock: PassphrasePrompt.Unlock? = null,
    val consent: ConsentStep.AskUser? = null,
    val message: BackupMessage? = null,
    val error: AppError? = null,
) {
    val isBusy: Boolean get() = busy != BackupBusy.NONE
    val isConnected: Boolean get() = auth is DriveAuthState.SignedIn
}

/**
 * The encryption setting on the backup screen. [prompt] stays until the passphrase is accepted or
 * the user cancels, [confirmTurnOff] until the user answers, and [message] and [error] until the
 * screen has shown them.
 */
@Immutable
data class EncryptionUiState(
    val isOn: Boolean = false,
    val isWorking: Boolean = false,
    val prompt: PassphrasePrompt.Set? = null,
    val confirmTurnOff: Boolean = false,
    val message: BackupMessage? = null,
    val error: AppError? = null,
)
