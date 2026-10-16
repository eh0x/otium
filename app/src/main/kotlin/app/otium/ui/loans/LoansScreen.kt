package app.otium.ui.loans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.OpenLoan
import app.otium.ui.common.formatDay
import app.otium.ui.common.messageRes
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumScreenHeader
import app.otium.ui.components.OtiumSnackbarHost
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

/** Connects the Lent out screen to its ViewModel. */
@Composable
fun LoansScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoansViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack, onOpenBook) {
        LoansActions(
            onBack = onBack,
            onOpenBook = onOpenBook,
            onReturned = viewModel::onReturned,
            onErrorShown = viewModel::onErrorShown,
        )
    }
    LoansContent(state = state, actions = actions, modifier = modifier)
}

/**
 * Every book that is out, or borrowed in: who has it, since when, and when a reminder is due.
 * The reminder day is shown here whether or not notifications are allowed.
 */
@Composable
fun LoansContent(state: LoansUiState, actions: LoansActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = state.error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            actions.onErrorShown()
        }
    }
    PaperBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            OtiumScreenHeader(
                title = stringResource(R.string.loans_screen_title),
                onBack = actions.onBack,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 22.dp,
                    end = 22.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
            ) {
                if (!state.isLoading && state.loans.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.loans_screen_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.otiumColors.muted,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                group(R.string.loans_lent_group, state.lent, actions)
                group(R.string.loans_borrowed_group, state.borrowed, actions)
            }
        }
        OtiumSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private fun LazyListScope.group(title: Int, loans: List<OpenLoan>, actions: LoansActions) {
    if (loans.isEmpty()) return
    item(key = "group:$title") {
        Text(
            text = stringResource(title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 4.dp)
                .semantics { heading() },
        )
    }
    items(items = loans, key = { it.loan.id }) { LoanRow(it, actions) }
}

@Composable
private fun LoanRow(item: OpenLoan, actions: LoansActions) {
    val loan = item.loan
    val muted = MaterialTheme.otiumColors.muted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .padding(vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button) { actions.onOpenBook(loan.bookId) },
        ) {
            Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (loan.direction == LoanDirection.LENT) {
                        R.string.loans_row_lent
                    } else {
                        R.string.loans_row_borrowed
                    },
                    loan.counterparty,
                    formatDay(loan.lentOn),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
            loan.remindOn?.let {
                Text(
                    text = stringResource(R.string.loan_reminder_on, formatDay(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
        }
        OtiumButton(
            text = stringResource(R.string.loan_returned),
            onClick = { actions.onReturned(loan.id) },
            style = OtiumButtonStyle.Secondary,
            compact = true,
            contentDescription = stringResource(R.string.loan_returned_description, item.title),
        )
    }
}
