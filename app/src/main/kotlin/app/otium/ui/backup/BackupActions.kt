package app.otium.ui.backup

import androidx.compose.runtime.Immutable
import app.otium.domain.model.BackupSettings
import app.otium.domain.model.BackupSlot

/**
 * Everything the backup screen can ask for, in one stable holder. Defaults do nothing, which keeps
 * tests and previews short.
 */
@Immutable
data class BackupActions(
    val onConnect: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onBackUpNow: () -> Unit = {},
    /** Download the backup in this slot and show what it holds, before anything changes. */
    val onRestore: (BackupSlot) -> Unit = {},
    /** Try again to open the encrypted backup in this slot with a passphrase; it is cleared. */
    val onUnlock: (BackupSlot, CharArray) -> Unit = { _, _ -> },
    val onRestoreConfirmed: () -> Unit = {},
    /** Cancel the restore, whether it waits for confirmation or for a passphrase. */
    val onRestoreDismissed: () -> Unit = {},
    val onSettingsChanged: (BackupSettings) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)

/** What the encryption setting on the backup screen can ask for. Defaults do nothing. */
@Immutable
data class EncryptionActions(
    /** Ask for a passphrase to turn encryption on, or to change it. */
    val onSetPassphrase: () -> Unit = {},
    /** The new passphrase and its confirmation; the receiver clears both arrays. */
    val onPassphraseEntered: (passphrase: CharArray, confirmation: CharArray) -> Unit = { _, _ -> },
    val onPassphraseDismissed: () -> Unit = {},
    val onTurnOff: () -> Unit = {},
    val onTurnOffConfirmed: () -> Unit = {},
    val onTurnOffDismissed: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)
