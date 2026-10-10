package app.otium.ui.backup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.DriveAuthState
import app.otium.ui.components.Hairline
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumIndeterminateRule
import app.otium.ui.components.isLargeFontScale
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors

/** Google Drive's state, the last backup, and "Back up now", in one bordered card. */
@Composable
internal fun DriveCard(state: BackupUiState, actions: BackupActions) {
    val colors = MaterialTheme.otiumColors
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.divider, shape)
            .padding(16.dp),
    ) {
        DriveRow(state, actions)
        if (state.busy == BackupBusy.CONNECTING) {
            OtiumIndeterminateRule(modifier = Modifier.padding(top = 12.dp))
        }
        Hairline(modifier = Modifier.padding(vertical = 14.dp))
        LastBackup(state.lastBackup)
        if (state.busy == BackupBusy.BACKING_UP) {
            OtiumIndeterminateRule(modifier = Modifier.padding(top = 12.dp))
        }
        val backingUp = state.busy == BackupBusy.BACKING_UP
        OtiumButton(
            text = stringResource(
                if (backingUp) R.string.backup_in_progress else R.string.backup_now,
            ),
            onClick = actions.onBackUpNow,
            enabled = state.isConnected && !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        )
    }
}

@Composable
private fun DriveRow(state: BackupUiState, actions: BackupActions) {
    val status = when (val auth = state.auth) {
        DriveAuthState.SignedOut -> stringResource(R.string.backup_drive_signed_out)

        DriveAuthState.Expired -> stringResource(R.string.backup_drive_expired)

        is DriveAuthState.SignedIn ->
            auth.account
                ?.let { stringResource(R.string.backup_drive_signed_in, it) }
                ?: stringResource(R.string.backup_drive_signed_in_unknown)
    }
    val (label, onClick) = when (state.auth) {
        DriveAuthState.SignedOut -> R.string.backup_connect to actions.onConnect
        DriveAuthState.Expired -> R.string.backup_reconnect to actions.onConnect
        is DriveAuthState.SignedIn -> R.string.backup_disconnect to actions.onDisconnect
    }
    val button = @Composable {
        OtiumButton(
            text = stringResource(label),
            onClick = onClick,
            style = OtiumButtonStyle.Ghost,
            compact = true,
            enabled = !state.isBusy,
        )
    }
    val large = isLargeFontScale()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_lucide_cloud),
            contentDescription = null,
            tint = MaterialTheme.otiumColors.accent,
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            SectionTitle(stringResource(R.string.backup_drive_heading))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.otiumColors.muted,
            )
        }
        // Beside the text normally; below it when text is very large, so neither is squeezed.
        if (!large) button()
    }
    if (large) button()
}

/** "LAST BACKUP", when, and what it held. */
@Composable
private fun LastBackup(last: BackupInfo?) {
    SmallCapsLabel(stringResource(R.string.backup_last_label))
    Text(
        text = if (last ==
            null
        ) {
            stringResource(R.string.backup_never)
        } else {
            formatDate(last.createdAt)
        },
        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
    )
    if (last != null) {
        val size = backupSizeText(last)
        Text(
            text = if (last.isEncrypted) {
                stringResource(R.string.dot_joined, size, stringResource(R.string.backup_encrypted))
            } else {
                size
            },
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = MaterialTheme.otiumColors.muted,
        )
    }
}
