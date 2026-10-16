package app.otium.domain.repo

import app.otium.domain.error.Try
import app.otium.domain.model.Loan
import app.otium.domain.model.OpenLoan
import kotlinx.coroutines.flow.Flow

/** The owner's lending ledger. Room is the source of truth; the implementation is in :app. */
interface LoanRepository {
    /** A book's loans, open and returned, newest first. */
    fun observeLoans(bookId: Long): Flow<List<Loan>>

    /** Every open loan, with its book, the oldest loan first. */
    fun observeOpenLoans(): Flow<List<OpenLoan>>

    /** Fails with AppError.NotFound when there is no such loan. */
    suspend fun getLoan(id: Long): Try<Loan>

    /** Every open loan, for planning reminders. */
    suspend fun openLoans(): Try<List<Loan>>

    /**
     * Fails with AppError.NotFound when the book does not exist, and with AppError.AlreadyOnLoan
     * when the loan is open and the book already has an open one. Returns the new id.
     */
    suspend fun add(loan: Loan): Try<Long>

    /** Replaces the loan's name, reminder and return. Fails with AppError.NotFound. */
    suspend fun update(loan: Loan): Try<Unit>

    suspend fun delete(id: Long): Try<Unit>
}
