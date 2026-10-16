package app.otium.domain.usecase

import app.otium.domain.error.Try
import app.otium.domain.port.Clock
import app.otium.domain.port.LoanReminderScheduler
import app.otium.domain.repo.LoanRepository

/**
 * Plans the reminder of every open loan that still has one ahead, for when loans arrive without
 * the ledger: after an import or a restore. A reminder whose day has passed is not planned, so a
 * restored library does not announce old news; the loan still shows on the Lent out screen.
 */
class SyncLoanReminders(
    private val loans: LoanRepository,
    private val reminders: LoanReminderScheduler,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val open = (loans.openLoans() as? Try.Success)?.value ?: return
        val today = clock.todayEpochDay()
        open.forEach { loan ->
            val remindOn = loan.remindOn
            if (remindOn != null && remindOn >= today) reminders.schedule(loan.id, remindOn)
        }
    }
}
