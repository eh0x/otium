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

/**
 * Connects the backup screen to its ViewModel and to the two system screens it needs: Google's
 * consent screen, and the permission prompt for the notification about failed backups.
 */
@Composable
fun BackupScreen(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
    encryptionViewModel: EncryptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val encryption by encryptionViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { viewModel.onConsentResult(ConsentResult(it.data)) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(state.consent) {
        state.consent?.let { step ->
            consentLauncher.launch(IntentSenderRequest.Builder(step.request).build())
            viewModel.onConsentLaunched()
        }
    }
    // The failure notice needs permission on newer Android; asking when the user turns backups on
    // puts the question where its reason is clear. Backups run whatever the answer is.
    LaunchedEffect(state.settings.autoBackup) {
        val needsAsking = state.settings.autoBackup &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsking) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val actions = remember(viewModel) {
        BackupActions(
            onConnect = viewModel::onConnect,
            onDisconnect = viewModel::onDisconnect,
            onBackUpNow = viewModel::onBackUpNow,
            onRestore = { slot -> viewModel.onRestore(slot) },
            onUnlock = viewModel::onRestore,
            onRestoreConfirmed = viewModel::onRestoreConfirmed,
            onRestoreDismissed = viewModel::onRestoreDismissed,
            onSettingsChanged = viewModel::onSettingsChanged,
            onMessageShown = viewModel::onMessageShown,
            onErrorShown = viewModel::onErrorShown,
        )
    }
    BackupContent(
        state = state,
        encryption = encryption,
        actions = actions,
        encryptionActions = rememberEncryptionActions(encryptionViewModel),
        modifier = modifier,
    )
}

@Composable
private fun rememberEncryptionActions(viewModel: EncryptionViewModel) = remember(viewModel) {
    EncryptionActions(
        onSetPassphrase = viewModel::onSetPassphrase,
        onPassphraseEntered = viewModel::onPassphraseEntered,
        onPassphraseDismissed = viewModel::onPassphraseDismissed,
        onTurnOff = viewModel::onTurnOff,
        onTurnOffConfirmed = viewModel::onTurnOffConfirmed,
        onTurnOffDismissed = viewModel::onTurnOffDismissed,
        onMessageShown = viewModel::onMessageShown,
        onErrorShown = viewModel::onErrorShown,
    )
}

/**
 * The backup screen as a pure function of [state] and [encryption], so it can be tested without
 * a ViewModel.
 */
@Composable
fun BackupContent(
    state: BackupUiState,
    actions: BackupActions,
    modifier: Modifier = Modifier,
    encryption: EncryptionUiState = EncryptionUiState(),
    encryptionActions: EncryptionActions = EncryptionActions(),
) {
    val snackbar = remember { SnackbarHostState() }
    Notices(snackbar, state.message, state.error, actions.onMessageShown, actions.onErrorShown)
    Notices(
        snackbar,
        encryption.message,
        encryption.error,
        encryptionActions.onMessageShown,
        encryptionActions.onErrorShown,
    )

    PaperBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 24.dp),
        ) {
            Heading()
            DriveCard(state, actions)
            Section { EncryptionSection(encryption, state.isBusy, encryptionActions) }
            Section { RestoreSection(state, actions) }
            Section { AutoBackupSection(state, actions) }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        state.restorePreview?.let { RestoreDialog(it, actions) }
        EncryptionDialogs(state, encryption, actions, encryptionActions)
    }
}

/** Shows [message] or [error] once in [snackbar], then reports it shown. */
@Composable
private fun Notices(
    snackbar: SnackbarHostState,
    message: BackupMessage?,
    error: AppError?,
    onMessageShown: () -> Unit,
    onErrorShown: () -> Unit,
) {
    val messageText = message?.let { stringResource(it.textRes()) }
    val errorText = error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageShown()
        }
    }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbar.showSnackbar(errorText)
            onErrorShown()
        }
    }
}

/** "Settings" over "Backup & restore", and what backups are. */
@Composable
private fun Heading() {
    Text(
        text = stringResource(R.string.settings_title).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.backup_title),
        style = MaterialTheme.typography.displaySmall.copy(lineHeight = 38.sp),
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(R.string.backup_drive_note),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        color = MaterialTheme.otiumColors.muted,
        modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
    )
}

/** A section under a hairline, spaced like the rest of the page. */
@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun RestoreSection(state: BackupUiState, actions: BackupActions) {
    SectionTitle(stringResource(R.string.restore_heading))
    Text(
        text = stringResource(R.string.restore_note),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        color = MaterialTheme.otiumColors.muted,
    )
    val canRestore = state.isConnected && !state.isBusy
    OtiumButton(
        text = stringResource(R.string.restore_action),
        onClick = { actions.onRestore(BackupSlot.LATEST) },
        style = OtiumButtonStyle.Secondary,
        enabled = canRestore,
        modifier = Modifier.fillMaxWidth(),
    )
    OtiumButton(
        text = stringResource(R.string.restore_previous_action),
        onClick = { actions.onRestore(BackupSlot.PREVIOUS) },
        style = OtiumButtonStyle.Ghost,
        compact = true,
        enabled = canRestore,
    )
    when (state.busy) {
        BackupBusy.PREPARING_RESTORE, BackupBusy.RESTORING ->
            BusyIndicator(stringResource(R.string.restore_in_progress))

        else -> Unit
    }
}

@Composable
private fun AutoBackupSection(state: BackupUiState, actions: BackupActions) {
    val settings = state.settings
    val enabled = state.isConnected && !state.isBusy
    SwitchRow(
        label = stringResource(R.string.auto_backup_heading),
        checked = settings.autoBackup,
        enabled = enabled,
        onCheckedChange = { actions.onSettingsChanged(settings.copy(autoBackup = it)) },
        title = true,
    )
    if (!state.isConnected) {
        Text(
            text = stringResource(R.string.auto_backup_needs_drive),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.otiumColors.muted,
        )
    }
    if (settings.autoBackup) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (frequency in BackupFrequency.entries) {
                OtiumChip(
                    label = stringResource(frequency.labelRes()),
                    selected = settings.frequency == frequency,
                    onClick = {
                        if (enabled) actions.onSettingsChanged(settings.copy(frequency = frequency))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        CheckboxRow(
            label = stringResource(R.string.auto_backup_wifi_only),
            checked = settings.wifiOnly,
            enabled = enabled,
            onCheckedChange = { actions.onSettingsChanged(settings.copy(wifiOnly = it)) },
        )
        CheckboxRow(
            label = stringResource(R.string.auto_backup_charging_only),
            checked = settings.chargingOnly,
            enabled = enabled,
            onCheckedChange = { actions.onSettingsChanged(settings.copy(chargingOnly = it)) },
        )
    }
}

/** Shows what the backup holds before it replaces the library, and asks the user to confirm. */
@Composable
private fun RestoreDialog(preview: RestorePreview, actions: BackupActions) {
    val info = preview.info
    OtiumDialog(
        title = stringResource(R.string.restore_confirm_title),
        body = stringResource(
            R.string.restore_confirm_message,
            pluralStringResource(R.plurals.backup_book_count, info.bookCount, info.bookCount),
            formatDate(info.createdAt),
        ),
        onDismissRequest = actions.onRestoreDismissed,
        actions = {
            OtiumButton(
                text = stringResource(R.string.restore_cancel),
                onClick = actions.onRestoreDismissed,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(
                text = stringResource(R.string.restore_confirm_action),
                onClick = actions.onRestoreConfirmed,
            )
        },
    )
}
