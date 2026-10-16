package app.otium.domain.fakes

import app.otium.domain.port.LoanReminderScheduler

/** Remembers which reminders are planned, as the real one would plan them. */
class FakeLoanReminderScheduler : LoanReminderScheduler {
    /** Loan id to the day (epoch day) it is planned for. */
    val planned = linkedMapOf<Long, Long>()

    override fun schedule(loanId: Long, remindOn: Long) {
        planned[loanId] = remindOn
    }

    override fun cancel(loanId: Long) {
        planned.remove(loanId)
    }
}
