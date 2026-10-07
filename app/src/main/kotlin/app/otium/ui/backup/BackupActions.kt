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
    val onBack: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onBackUpNow: () -> Unit = {},
    /** Download the backup in this slot and show what it holds, before anything changes. */
    val onRestore: (BackupSlot) -> Unit = {},
    val onRestoreConfirmed: () -> Unit = {},
    val onRestoreDismissed: () -> Unit = {},
    val onSettingsChanged: (BackupSettings) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onErrorShown: () -> Unit = {},
)
