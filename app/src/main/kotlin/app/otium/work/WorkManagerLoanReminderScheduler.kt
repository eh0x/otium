package app.otium.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.otium.domain.port.Clock
import app.otium.domain.port.LoanReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The hour of the reminder's day when it is said: after breakfast, not at midnight. */
private const val REMIND_HOUR = 9

/**
 * Plans [LoanReminderWorker] as unique work for each loan, to run at 9:00 local time on the
 * reminder's day (at once when that has passed). WorkManager keeps the plan through restarts. It
 * runs when the system allows rather than to the minute, which suits a quiet nudge. Planning a
 * loan again replaces its earlier reminder.
 */
class WorkManagerLoanReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : LoanReminderScheduler {

    override fun schedule(loanId: Long, remindOn: Long) {
        val dueAt = LocalDate.ofEpochDay(remindOn)
            .atTime(REMIND_HOUR, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val delay = (dueAt - clock.nowMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<LoanReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(LoanReminderWorker.KEY_LOAN_ID, loanId).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LoanReminderWorker.uniqueName(loanId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(loanId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(LoanReminderWorker.uniqueName(loanId))
    }
}
