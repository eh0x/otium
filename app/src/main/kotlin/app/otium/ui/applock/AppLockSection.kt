package app.otium.ui.applock

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.applock.AuthPrompt
import app.otium.applock.LockAvailability
import app.otium.applock.findFragmentActivity
import app.otium.domain.model.LockTimeout
import app.otium.ui.backup.SmallCapsLabel
import app.otium.ui.backup.SwitchRow
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumChip
import app.otium.ui.theme.otiumColors

/**
 * The app lock setting on the settings page: a switch, what it does, and how soon it asks again.
 * Without a screen lock on the phone, it says how to set one instead.
 */
@Composable
fun AppLockSection(state: AppLockUiState, actions: AppLockActions, enabled: Boolean = true) {
    val available = state.availability == LockAvailability.AVAILABLE
    SwitchRow(
        label = stringResource(R.string.app_lock_heading),
        checked = state.enabled,
        enabled = enabled && (available || state.enabled),
        onCheckedChange = actions.onToggle,
        title = true,
        icon = R.drawable.ic_lucide_lock,
    )
    Note(
        stringResource(
            when {
                !available -> R.string.app_lock_no_screen_lock
                state.enabled -> R.string.app_lock_note_on
                else -> R.string.app_lock_note_off
            },
        ),
    )
    if (!available) {
        OtiumButton(
            text = stringResource(R.string.app_lock_open_settings),
            onClick = actions.onOpenSecuritySettings,
            style = OtiumButtonStyle.Ghost,
            compact = true,
        )
    }
    state.failure?.let { Note(it) }
    if (state.enabled) {
        SmallCapsLabel(stringResource(R.string.app_lock_timeout_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (timeout in LockTimeout.entries) {
                OtiumChip(
                    label = stringResource(timeout.labelRes()),
                    selected = state.timeout == timeout,
                    onClick = { if (enabled) actions.onTimeout(timeout) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        color = MaterialTheme.otiumColors.muted,
    )
}

private fun LockTimeout.labelRes(): Int = when (this) {
    LockTimeout.ONE_MINUTE -> R.string.app_lock_timeout_one_minute
    LockTimeout.FIVE_MINUTES -> R.string.app_lock_timeout_five_minutes
}

/** The app lock actions for the settings page: each toggle shows the system prompt over the activity. */
@Composable
internal fun rememberAppLockActions(viewModel: AppLockViewModel): AppLockActions {
    val context = LocalContext.current
    val onTitle = stringResource(R.string.app_lock_prompt_on_title)
    val offTitle = stringResource(R.string.app_lock_prompt_off_title)
    val subtitle = stringResource(R.string.app_lock_prompt_subtitle)
    return remember(viewModel, context) {
        AppLockActions(
            onToggle = { on ->
                context.findFragmentActivity()?.let { activity ->
                    viewModel.onToggle(
                        activity,
                        on,
                        AuthPrompt(if (on) onTitle else offTitle, subtitle),
                    )
                }
            },
            onTimeout = viewModel::onTimeout,
            onOpenSecuritySettings = {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            },
        )
    }
}
