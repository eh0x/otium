package app.otium.ui.backup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.otium.R
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumDialog
import app.otium.ui.theme.otiumColors

/**
 * Whether backups are encrypted, as a switch: turning it on asks for a passphrase, turning it off
 * asks for confirmation. While on, the passphrase can be changed.
 */
@Composable
internal fun EncryptionSection(
    encryption: EncryptionUiState,
    otherWorkRunning: Boolean,
    actions: EncryptionActions,
) {
    val enabled = !otherWorkRunning && !encryption.isWorking
    val on = encryption.isOn
    SwitchRow(
        label = stringResource(R.string.encryption_heading),
        checked = on,
        enabled = enabled,
        onCheckedChange = { if (it) actions.onSetPassphrase() else actions.onTurnOff() },
        title = true,
        icon = R.drawable.ic_lucide_lock,
    )
    Text(
        text = stringResource(if (on) R.string.encryption_on else R.string.encryption_off),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        color = MaterialTheme.otiumColors.muted,
    )
    if (encryption.isWorking) BusyIndicator(stringResource(R.string.encryption_working))
    if (on) {
        OtiumButton(
            text = stringResource(R.string.encryption_change),
            onClick = actions.onSetPassphrase,
            style = OtiumButtonStyle.Ghost,
            compact = true,
            enabled = enabled,
        )
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

/** [PassphrasePanel] in a dialog window, sized by its content with the page gutter at the sides. */
@Composable
private fun PassphraseDialog(
    prompt: PassphrasePrompt,
    busy: Boolean,
    onSubmit: (passphrase: CharArray, confirmation: CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.padding(horizontal = 22.dp)) {
            PassphrasePanel(prompt, busy, onSubmit, onDismiss)
        }
    }
}

/** Confirms turning encryption off, saying what happens to the backups already made. */
@Composable
private fun TurnOffEncryptionDialog(actions: EncryptionActions) {
    OtiumDialog(
        title = stringResource(R.string.encryption_off_title),
        body = stringResource(R.string.encryption_off_message),
        onDismissRequest = actions.onTurnOffDismissed,
        actions = {
            OtiumButton(
                text = stringResource(R.string.restore_cancel),
                onClick = actions.onTurnOffDismissed,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(
                text = stringResource(R.string.encryption_off_action),
                onClick = actions.onTurnOffConfirmed,
            )
        },
    )
}
