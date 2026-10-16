package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.port.Clock
import app.otium.domain.port.LoanReminderScheduler
import app.otium.domain.repo.LoanRepository
import kotlinx.coroutines.flow.Flow

/**
 * Keeps the owner's lending ledger: who has a book of theirs, and which book they have borrowed.
 * The name is trimmed and must not be blank or longer than [Loan.MAX_NAME_LENGTH]. Days are
 * counted from today: the book went today or before, and a reminder is at least a day ahead. A
 * book has one open loan at a time. A reminder is planned only while the loan is open.
 */
class LendingLedger(
    private val loans: LoanRepository,
    private val reminders: LoanReminderScheduler,
    private val clock: Clock,
) {
    /** A book's loans, open and returned, newest first. */
    fun loansOf(bookId: Long): Flow<List<Loan>> = loans.observeLoans(bookId)

    /**
     * Opens a loan of book [bookId], begun [daysAgo] days ago (0 for today), with a reminder
     * [remindInDays] days from today, or none. Returns the loan's id.
     */
    suspend fun open(
        bookId: Long,
        direction: LoanDirection,
        name: String,
        daysAgo: Int,
        remindInDays: Int?,
    ): Try<Long> {
        val cleaned = Loan.cleanName(name) ?: return Try.Failure(AppError.InvalidInput("name"))
        val invalid = when {
            daysAgo < 0 -> "lentOn"
            remindInDays != null && remindInDays < 1 -> "remindOn"
            else -> null
        }
        if (invalid != null) return Try.Failure(AppError.InvalidInput(invalid))
        val today = clock.todayEpochDay()
        val remindOn = remindInDays?.let { today + it }
        val loan = Loan(0L, bookId, cleaned, direction, today - daysAgo, remindOn)
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

    /**
     * Sets or moves the reminder of an open loan to [remindInDays] days from today, or clears it
     * with null.
     */
    suspend fun changeReminder(loanId: Long, remindInDays: Int?): Try<Unit> =
        loans.getLoan(loanId).flatMap { loan ->
            val remindOn = remindInDays?.let { clock.todayEpochDay() + it }
            when {
                !loan.isOpen -> Try.Failure(AppError.InvalidInput("loan"))

                remindInDays != null && remindInDays < 1 ->
                    Try.Failure(AppError.InvalidInput("remindOn"))

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
}
