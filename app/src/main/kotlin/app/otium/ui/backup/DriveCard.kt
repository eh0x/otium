package app.otium.ui.backup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.auth.ConsentResult
import app.otium.domain.error.AppError
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.DriveAuthState
import app.otium.domain.model.RestorePreview
import app.otium.ui.common.messageRes
import app.otium.ui.components.Hairline
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumChip
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumIndeterminateRule
import app.otium.ui.components.OtiumTopBar
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors
import java.text.DateFormat
import java.util.Date

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
        OtiumButton(
            text = stringResource(label),
            onClick = onClick,
            style = OtiumButtonStyle.Ghost,
            compact = true,
            enabled = !state.isBusy,
        )
    }
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
        style = MaterialTheme.typography.headlineSmall,
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
