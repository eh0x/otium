package app.otium.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.otium.domain.usecase.PrepareLoanReminder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Says the reminder of one loan, if there is still something to say (see [PrepareLoanReminder]).
 * It always ends as a success: a reminder that cannot be shown is not worth trying again.
 */
@HiltWorker
class LoanReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prepare: PrepareLoanReminder,
    private val notifier: LoanReminderNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val loanId = inputData.getLong(KEY_LOAN_ID, NO_LOAN)
        if (loanId != NO_LOAN) prepare(loanId)?.let { notifier.remind(it) }
        return Result.success()
    }

    companion object {
        const val KEY_LOAN_ID = "loanId"
        private const val NO_LOAN = -1L

        /** The unique work of one loan's reminder, so planning again replaces it. */
        fun uniqueName(loanId: Long) = "loan-reminder-$loanId"
    }
}
