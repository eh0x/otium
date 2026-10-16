package app.otium.domain.port

/**
 * Arranges for the owner to be reminded, once and quietly, on the day a loan should be thought
 * about. A reminder with the same loan replaces the earlier one.
 */
interface LoanReminderScheduler {
    /** Plans the reminder of loan [loanId] for the day [remindOn] (epoch day). */
    fun schedule(loanId: Long, remindOn: Long)

    /** Drops the reminder of loan [loanId], if there is one. */
    fun cancel(loanId: Long)
}
