package app.otium.ui.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.repo.LoanRepository
import app.otium.domain.usecase.LendingLedger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** State holder for the Lent out screen: the open loans, live, and returning one from the list. */
@HiltViewModel
class LoansViewModel @Inject constructor(
    loans: LoanRepository,
    private val ledger: LendingLedger,
) : ViewModel() {

    private val error = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<LoansUiState> = combine(loans.observeOpenLoans(), error) {
            open,
            failure,
        ->
        LoansUiState(isLoading = false, loans = open, error = failure)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LoansUiState(),
    )

    fun onReturned(loanId: Long) {
        viewModelScope.launch {
            val result = ledger.markReturned(loanId)
            if (result is Try.Failure) error.update { result.error }
        }
    }

    fun onErrorShown() = error.update { null }
}
