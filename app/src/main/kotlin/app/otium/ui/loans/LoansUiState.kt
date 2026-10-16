package app.otium.ui.loans

import androidx.compose.runtime.Immutable
import app.otium.domain.error.AppError
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.OpenLoan

/** Everything the Lent out screen shows: the open loans, in the order they began. */
@Immutable
data class LoansUiState(
    val isLoading: Boolean = true,
    val loans: List<OpenLoan> = emptyList(),
    val error: AppError? = null,
) {
    /** Books the owner lent to someone. */
    val lent: List<OpenLoan> get() = loans.filter { it.loan.direction == LoanDirection.LENT }

    /** Books the owner borrowed. */
    val borrowed: List<OpenLoan> get() = loans.filter {
        it.loan.direction == LoanDirection.BORROWED
    }
}

/** Everything the Lent out screen can ask for. Defaults do nothing, which keeps tests short. */
@Immutable
data class LoansActions(
    val onBack: () -> Unit = {},
    val onOpenBook: (Long) -> Unit = {},
    val onReturned: (Long) -> Unit = {},
    val onErrorShown: () -> Unit = {},
)
