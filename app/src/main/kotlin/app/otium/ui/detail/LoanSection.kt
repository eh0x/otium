package app.otium.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.ui.common.formatDay
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.otiumColors

/**
 * Who has this book, or whose it is: lend it out or note it as borrowed, mark it returned, set a
 * reminder, and the history of earlier loans, one line each. A name can be removed from the
 * history at any time. The name stays on the device (and in a backup or export the owner makes).
 */
@Composable
internal fun LoanSection(
    loans: List<Loan>,
    actions: DetailActions,
    modifier: Modifier = Modifier,
) {
    var lending by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var removingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = loans.firstOrNull { it.isOpen }
    val history = loans.filterNot { it.isOpen }

    Column(modifier = modifier) {
        LoanHeader(canAdd = open == null, onAdd = { lending = it.name })
        when {
            open != null -> OpenLoanItem(
                loan = open,
                onReturned = { actions.onLoanReturned(open.id) },
                onReminder = { reminderFor = open.id },
            )

            history.isEmpty() -> QuietText(stringResource(R.string.loans_none))
        }
        history.forEach { loan ->
            HistoryLine(loan = loan, onRemove = { removingId = loan.id })
        }
    }

    lending?.let { name ->
        LoanDialog(
            direction = LoanDirection.valueOf(name),
            onConfirm = {
                lending = null
                actions.onLoanOpened(it)
            },
            onDismiss = { lending = null },
        )
    }
    reminderFor?.let { id ->
        ReminderDialog(
            onConfirm = {
                reminderFor = null
                actions.onReminderChanged(id, it)
            },
            onDismiss = { reminderFor = null },
        )
    }
    removingId?.let { id ->
        RemoveLoanDialog(
            onConfirm = {
                removingId = null
                actions.onLoanRemoved(id)
            },
            onDismiss = { removingId = null },
        )
    }
}

@Composable
private fun LoanHeader(canAdd: Boolean, onAdd: (LoanDirection) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.loans_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (canAdd) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OtiumButton(
                    text = stringResource(R.string.loans_lend),
                    onClick = { onAdd(LoanDirection.LENT) },
                    style = OtiumButtonStyle.Secondary,
                    compact = true,
                )
                OtiumButton(
                    text = stringResource(R.string.loans_borrowed),
                    onClick = { onAdd(LoanDirection.BORROWED) },
                    style = OtiumButtonStyle.Secondary,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun OpenLoanItem(loan: Loan, onReturned: () -> Unit, onReminder: () -> Unit) {
    val muted = MaterialTheme.otiumColors.muted
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(text = loan.headline(), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.loan_since, formatDay(loan.lentOn)),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        Text(
            text = loan.remindOn?.let { stringResource(R.string.loan_reminder_on, formatDay(it)) }
                ?: stringResource(R.string.loan_no_reminder),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OtiumButton(
                text = stringResource(R.string.loan_returned),
                onClick = onReturned,
                style = OtiumButtonStyle.Secondary,
                compact = true,
            )
            OtiumButton(
                text = stringResource(R.string.loan_reminder_action),
                onClick = onReminder,
                style = OtiumButtonStyle.Secondary,
                compact = true,
            )
        }
    }
}

/** One line of history: who, and from when to when. */
@Composable
private fun HistoryLine(loan: Loan, onRemove: () -> Unit) {
    val returned = loan.returnedOn ?: loan.lentOn
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .padding(top = 8.dp),
    ) {
        Text(
            text = stringResource(
                if (loan.direction == LoanDirection.LENT) {
                    R.string.loan_history_lent
                } else {
                    R.string.loan_history_borrowed
                },
                loan.counterparty,
                formatDay(loan.lentOn),
                formatDay(returned),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier.weight(1f),
        )
        OtiumButton(
            text = stringResource(R.string.loan_remove),
            onClick = onRemove,
            style = OtiumButtonStyle.Secondary,
            compact = true,
            contentDescription = stringResource(
                R.string.loan_remove_description,
                loan.counterparty,
            ),
        )
    }
}

@Composable
private fun QuietText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.otiumColors.muted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun Loan.headline(): String = stringResource(
    if (direction == LoanDirection.LENT) R.string.loan_open_lent else R.string.loan_open_borrowed,
    counterparty,
)
