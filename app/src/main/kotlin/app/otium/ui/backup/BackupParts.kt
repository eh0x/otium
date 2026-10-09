package app.otium.ui.backup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.auth.ConsentResult
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupInfo
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.DriveAuthState
import app.otium.domain.model.RestorePreview
import app.otium.ui.common.messageRes
import app.otium.ui.components.OtiumTopBar
import app.otium.ui.theme.PaperBackground
import java.text.DateFormat
import java.util.Date

/** A labelled switch where the whole row is the target, as one control for screen readers. */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun BusyIndicator(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun lastBackupText(info: BackupInfo): String = stringResource(
    R.string.backup_last,
    formatDate(info.createdAt),
    pluralStringResource(R.plurals.backup_book_count, info.bookCount, info.bookCount),
    Formatter.formatShortFileSize(LocalContext.current, info.sizeBytes),
)

@Composable
internal fun formatDate(epochMillis: Long): String {
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    return format.format(Date(epochMillis))
}

internal fun BackupMessage.textRes(): Int = when (this) {
    BackupMessage.BACKED_UP -> R.string.backup_done
    BackupMessage.RESTORED -> R.string.restore_done
    BackupMessage.ENCRYPTION_ON -> R.string.encryption_on_done
    BackupMessage.ENCRYPTION_OFF -> R.string.encryption_off_done
}

internal fun BackupFrequency.labelRes(): Int = when (this) {
    BackupFrequency.DAILY -> R.string.auto_backup_daily
    BackupFrequency.WEEKLY -> R.string.auto_backup_weekly
}
