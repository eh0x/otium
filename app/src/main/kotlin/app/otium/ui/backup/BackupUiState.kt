package app.otium.ui.backup

import androidx.compose.runtime.Immutable
import app.otium.auth.ConsentStep
import app.otium.domain.error.AppError
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSettings
import app.otium.domain.model.DriveAuthState
import app.otium.domain.model.RestorePreview

/** What the screen is waiting on. Only one thing runs at a time. */
enum class BackupBusy { NONE, CONNECTING, BACKING_UP, PREPARING_RESTORE, RESTORING }

/** Something that went right, shown briefly. */
enum class BackupMessage { BACKED_UP, RESTORED }

/**
 * Everything the backup screen shows. Outcomes are state, not one-off events: [message] and
 * [error] stay until the screen has shown them, [consent] until it has launched Google's screen,
 * and [restorePreview] until the user confirms or cancels the restore.
 */
@Immutable
data class BackupUiState(
    val auth: DriveAuthState = DriveAuthState.SignedOut,
    val settings: BackupSettings = BackupSettings(),
    val lastBackup: BackupInfo? = null,
    val busy: BackupBusy = BackupBusy.NONE,
    val restorePreview: RestorePreview? = null,
    val consent: ConsentStep.AskUser? = null,
    val message: BackupMessage? = null,
    val error: AppError? = null,
) {
    val isBusy: Boolean get() = busy != BackupBusy.NONE
    val isConnected: Boolean get() = auth is DriveAuthState.SignedIn
}
