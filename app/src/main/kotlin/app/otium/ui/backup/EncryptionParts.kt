package app.otium.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otium.R

/** Whether backups are encrypted, and the buttons to set, change or drop the passphrase. */
@Composable
internal fun EncryptionSection(
    encryption: EncryptionUiState,
    otherWorkRunning: Boolean,
    actions: EncryptionActions,
) {
    val enabled = !otherWorkRunning && !encryption.isWorking
    val on = encryption.isOn
    Text(
        text = stringResource(if (on) R.string.encryption_on else R.string.encryption_off),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (!encryption.isOn) {
        Text(
            text = stringResource(R.string.encryption_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (encryption.isWorking) BusyIndicator(stringResource(R.string.encryption_working))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onSetPassphrase, enabled = enabled) {
            Text(
                stringResource(if (on) R.string.encryption_change else R.string.encryption_turn_on),
            )
        }
        if (encryption.isOn) {
            TextButton(onClick = actions.onTurnOff, enabled = enabled) {
                Text(stringResource(R.string.encryption_turn_off))
            }
        }
    }
}

/** The dialogs encryption can open over the backup screen: at most one is open at a time. */
@Composable
internal fun EncryptionDialogs(
    state: BackupUiState,
    encryption: EncryptionUiState,
    actions: BackupActions,
    encryptionActions: EncryptionActions,
) {
    val unlock = state.unlock
    val prompt = encryption.prompt
    when {
        unlock != null -> PassphraseDialog(
            prompt = unlock,
            busy = state.busy == BackupBusy.PREPARING_RESTORE,
            onSubmit = { passphrase, confirmation ->
                confirmation.fill(Char.MIN_VALUE)
                actions.onUnlock(unlock.slot, passphrase)
            },
            onDismiss = actions.onRestoreDismissed,
        )

        prompt != null -> PassphraseDialog(
            prompt = prompt,
            busy = encryption.isWorking,
            onSubmit = encryptionActions.onPassphraseEntered,
            onDismiss = encryptionActions.onPassphraseDismissed,
        )

        encryption.confirmTurnOff -> TurnOffEncryptionDialog(encryptionActions)
    }
}

/** [PassphrasePanel] in a dialog window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PassphraseDialog(
    prompt: PassphrasePrompt,
    busy: Boolean,
    onSubmit: (passphrase: CharArray, confirmation: CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = { if (!busy) onDismiss() }) {
        PassphrasePanel(prompt, busy, onSubmit, onDismiss)
    }
}

/** Confirms turning encryption off, saying what happens to the backups already made. */
@Composable
private fun TurnOffEncryptionDialog(actions: EncryptionActions) {
    AlertDialog(
        onDismissRequest = actions.onTurnOffDismissed,
        title = { Text(stringResource(R.string.encryption_off_title)) },
        text = { Text(stringResource(R.string.encryption_off_message)) },
        confirmButton = {
            TextButton(onClick = actions.onTurnOffConfirmed) {
                Text(stringResource(R.string.encryption_off_action))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onTurnOffDismissed) {
                Text(stringResource(R.string.restore_cancel))
            }
        },
    )
}
