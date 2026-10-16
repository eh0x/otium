package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.port.Clock
import app.otium.domain.port.LoanReminderScheduler
import app.otium.domain.repo.LoanRepository

/**
 * Keeps the owner's lending ledger: who has a book of theirs, and which book they have borrowed.
 * The name is trimmed and must not be blank or longer than [Loan.MAX_NAME_LENGTH]; the day the
 * book went is not in the future; a reminder is on a day after today and after the loan began. A
 * book has one open loan at a time. A reminder is planned only while the loan is open.
 */
class LendingLedger(
    private val loans: LoanRepository,
    private val reminders: LoanReminderScheduler,
    private val clock: Clock,
) {
    /** Opens a loan of book [bookId] and returns its id. */
    suspend fun open(
        bookId: Long,
        direction: LoanDirection,
        name: String,
        lentOn: Long,
        remindOn: Long?,
    ): Try<Long> {
        val cleaned = Loan.cleanName(name) ?: return Try.Failure(AppError.InvalidInput("name"))
        val invalid = invalidDate(lentOn, remindOn)
        if (invalid != null) return Try.Failure(AppError.InvalidInput(invalid))
        val loan = Loan(0L, bookId, cleaned, direction, lentOn, remindOn)
        return loans.add(loan).also { added ->
            if (added is Try.Success && remindOn != null) reminders.schedule(added.value, remindOn)
        }
    }

    /** Marks the loan returned today, or on the day it began if that is later. */
    suspend fun markReturned(loanId: Long): Try<Unit> = loans.getLoan(loanId).flatMap { loan ->
        if (!loan.isOpen) return@flatMap Try.Success(Unit)
        val returnedOn = maxOf(clock.todayEpochDay(), loan.lentOn)
        loans.update(loan.copy(returnedOn = returnedOn)).also {
            if (it is Try.Success) reminders.cancel(loanId)
        }
    }

    /** Sets, moves or (with null) clears the reminder of an open loan. */
    suspend fun changeReminder(loanId: Long, remindOn: Long?): Try<Unit> =
        loans.getLoan(loanId).flatMap { loan ->
            val invalid = if (remindOn == null) null else invalidDate(loan.lentOn, remindOn)
            when {
                !loan.isOpen -> Try.Failure(AppError.InvalidInput("loan"))

                invalid != null -> Try.Failure(AppError.InvalidInput(invalid))

                else -> loans.update(loan.copy(remindOn = remindOn)).also {
                    if (it is Try.Success) {
                        if (remindOn == null) {
                            reminders.cancel(loanId)
                        } else {
                            reminders.schedule(loanId, remindOn)
                        }
                    }
                }
            }
        }

    /** Takes a loan, open or returned, out of the ledger. */
    suspend fun remove(loanId: Long): Try<Unit> = loans.delete(loanId).also {
        if (it is Try.Success) reminders.cancel(loanId)
    }

    /** The name of the first date that is not acceptable, or null when they all are. */
    private fun invalidDate(lentOn: Long, remindOn: Long?): String? = when {
        lentOn > clock.todayEpochDay() -> "lentOn"
        remindOn != null && (remindOn <= clock.todayEpochDay() || remindOn <= lentOn) -> "remindOn"
        else -> null
    }
}
