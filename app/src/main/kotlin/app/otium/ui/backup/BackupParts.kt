package app.otium.ui.backup

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupInfo
import app.otium.ui.components.OtiumIndeterminateRule
import app.otium.ui.components.OtiumSwitch
import app.otium.ui.theme.otiumColors
import java.text.DateFormat
import java.util.Date

/**
 * A labelled switch where the whole row is the target, as one control for screen readers.
 * [title] sets the label in the serif title style, for a row that heads its section.
 */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: Boolean = false,
    icon: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.otiumColors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = if (title) {
                MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            modifier = Modifier.weight(1f),
        )
        OtiumSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A labelled checkbox where the whole row is the target. */
@Composable
internal fun CheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OtiumCheckbox(checked = checked, enabled = enabled)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A checkbox in the accent color; its row carries the toggle. */
@Composable
internal fun OtiumCheckbox(checked: Boolean, enabled: Boolean = true) {
    val colors = MaterialTheme.otiumColors
    Checkbox(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = colors.accent,
            uncheckedColor = colors.muted,
            checkmarkColor = MaterialTheme.colorScheme.background,
        ),
    )
}

/** Work going on: the indeterminate rule over a quiet label. */
@Composable
internal fun BusyIndicator(label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OtiumIndeterminateRule()
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.otiumColors.muted,
        )
    }
}

/** A section title in the serif title style. */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
        modifier = Modifier.semantics { heading() },
    )
}

/** A small capitals label above a value, such as "LAST BACKUP". */
@Composable
internal fun SmallCapsLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.otiumColors.muted,
    )
}

/** "12 books · 1.2 MB": what a backup holds. */
@Composable
internal fun backupSizeText(info: BackupInfo): String = stringResource(
    R.string.dot_joined,
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
