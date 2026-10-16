package app.otium.ui.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumChip
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumTextField
import app.otium.ui.theme.otiumColors

private val DIALOG_MAX_HEIGHT = 420.dp

/** When the book went, as the owner picks it: counted back from today, so no date is typed. */
internal enum class WhenOption(val daysAgo: Int, @StringRes val label: Int) {
    TODAY(0, R.string.loan_when_today),
    YESTERDAY(1, R.string.loan_when_yesterday),
    WEEK_AGO(7, R.string.loan_when_week),
}

/** When to be reminded, counted from today. [inDays] is null for no reminder. */
internal enum class RemindOption(val inDays: Int?, @StringRes val label: Int) {
    NONE(null, R.string.loan_remind_none),
    WEEK(7, R.string.loan_remind_week),
    TWO_WEEKS(14, R.string.loan_remind_two_weeks),
    MONTH(30, R.string.loan_remind_month),
}

/**
 * What the loan dialog hands back. The days are counted from today and turned into dates where
 * the clock is, so the screen never works with dates itself.
 */
@Immutable
data class LoanDraft(
    val direction: LoanDirection,
    val name: String,
    val daysAgo: Int,
    val remindInDays: Int?,
)

@Immutable
private data class LoanForm(
    val name: String,
    val whenOption: WhenOption,
    val remind: RemindOption,
) {
    val trimmed: String get() = name.trim()
    val nameOk: Boolean get() = Loan.cleanName(name) != null

    companion object {
        /** Survives rotation and the process being recreated. */
        val Saver = listSaver<LoanForm, String>(
            save = { listOf(it.name, it.whenOption.name, it.remind.name) },
            restore = {
                LoanForm(it[0], WhenOption.valueOf(it[1]), RemindOption.valueOf(it[2]))
            },
        )
    }
}

/**
 * The form for lending a book out or noting one borrowed in. The name is typed by hand; no
 * contact is read and nothing is sent to the other person. Save needs a usable name.
 */
@Composable
internal fun LoanDialog(
    direction: LoanDirection,
    onConfirm: (LoanDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var form by rememberSaveable(stateSaver = LoanForm.Saver) {
        mutableStateOf(LoanForm("", WhenOption.TODAY, RemindOption.NONE))
    }
    val lent = direction == LoanDirection.LENT
    OtiumDialog(
        title = stringResource(
            if (lent) R.string.loan_lend_title else R.string.loan_borrowed_title,
        ),
        onDismissRequest = onDismiss,
        actions = {
            OtiumButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(
                text = stringResource(R.string.action_save),
                onClick = {
                    onConfirm(
                        LoanDraft(
                            direction,
                            form.trimmed,
                            form.whenOption.daysAgo,
                            form.remind.inDays,
                        ),
                    )
                },
                enabled = form.nameOk,
            )
        },
    ) {
        LoanFields(form = form, lent = lent, onChange = { form = it })
    }
}

/** The form's fields, in a column that scrolls so a large font never hides the buttons. */
@Composable
private fun LoanFields(form: LoanForm, lent: Boolean, onChange: (LoanForm) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .heightIn(max = DIALOG_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
    ) {
        OtiumTextField(
            value = form.name,
            onValueChange = { onChange(form.copy(name = it.take(Loan.MAX_NAME_LENGTH))) },
            label = stringResource(
                if (lent) R.string.loan_name_label_lent else R.string.loan_name_label_borrowed,
            ),
        )
        OptionRow(
            label = stringResource(R.string.loan_when_label),
            options = WhenOption.entries,
            selected = form.whenOption,
            labelOf = { it.label },
            onSelect = { onChange(form.copy(whenOption = it)) },
        )
        OptionRow(
            label = stringResource(R.string.loan_remind_label),
            options = RemindOption.entries,
            selected = form.remind,
            labelOf = { it.label },
            onSelect = { onChange(form.copy(remind = it)) },
        )
        if (form.remind != RemindOption.NONE) RemindHint()
    }
}

@Composable
private fun RemindHint() {
    Text(
        text = stringResource(R.string.loan_remind_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.otiumColors.muted,
    )
}

/** Changes, or with "No", clears the reminder of an open loan. */
@Composable
internal fun ReminderDialog(onConfirm: (Int?) -> Unit, onDismiss: () -> Unit) {
    var remind by rememberSaveable { mutableStateOf(RemindOption.WEEK) }
    OtiumDialog(
        title = stringResource(R.string.loan_reminder_title),
        onDismissRequest = onDismiss,
        actions = {
            OtiumButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(
                text = stringResource(R.string.action_save),
                onClick = { onConfirm(remind.inDays) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OptionRow(
                label = stringResource(R.string.loan_remind_label),
                options = RemindOption.entries,
                selected = remind,
                labelOf = { it.label },
                onSelect = { remind = it },
            )
            if (remind != RemindOption.NONE) RemindHint()
        }
    }
}

@Composable
internal fun RemoveLoanDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    OtiumDialog(
        title = stringResource(R.string.loan_remove_title),
        body = stringResource(R.string.loan_remove_message),
        onDismissRequest = onDismiss,
        actions = {
            OtiumButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(text = stringResource(R.string.action_delete), onClick = onConfirm)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.otiumColors.muted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                OtiumChip(
                    label = stringResource(labelOf(option)),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}
