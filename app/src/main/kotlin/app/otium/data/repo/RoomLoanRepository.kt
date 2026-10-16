package app.otium.data.repo

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import app.otium.data.cover.CoverFiles
import app.otium.data.cover.coversDirectory
import app.otium.data.local.dao.LoanDao
import app.otium.data.local.mapper.toDomain
import app.otium.data.local.mapper.toEntity
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Loan
import app.otium.domain.model.OpenLoan
import app.otium.domain.repo.LoanRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The owner's lending ledger in Room. A book's loans are deleted with it by the foreign key. */
class RoomLoanRepository internal constructor(
    private val dao: LoanDao,
    private val covers: CoverFiles,
) : LoanRepository {

    @Inject
    internal constructor(
        dao: LoanDao,
        @ApplicationContext context: Context,
    ) : this(dao, CoverFiles(coversDirectory(context)))

    override fun observeLoans(bookId: Long): Flow<List<Loan>> =
        dao.observeLoans(bookId).map { rows -> rows.map { it.toDomain() } }

    override fun observeOpenLoans(): Flow<List<OpenLoan>> =
        dao.observeOpenLoans().map { rows -> rows.map { it.toDomain(covers) } }

    override suspend fun getLoan(id: Long): Try<Loan> = guard {
        dao.get(id)?.let { Try.Success(it.toDomain()) } ?: Try.Failure(AppError.NotFound)
    }

    override suspend fun openLoans(): Try<List<Loan>> = guard {
        Try.Success(dao.openLoans().map { it.toDomain() })
    }

    override suspend fun add(loan: Loan): Try<Long> = guard {
        try {
            dao.insertUnlessOpen(loan.toEntity().copy(id = 0L))
                ?.let { Try.Success(it) }
                ?: Try.Failure(AppError.AlreadyOnLoan)
        } catch (_: SQLiteConstraintException) {
            // The only constraint a loan can break is the book it belongs to.
            Try.Failure(AppError.NotFound)
        }
    }

    override suspend fun update(loan: Loan): Try<Unit> = guard {
        val changed = dao.updateContent(
            loan.id,
            loan.counterparty,
            loan.remindOn,
            loan.returnedOn,
        )
        if (changed > 0) Try.Success(Unit) else Try.Failure(AppError.NotFound)
    }

    override suspend fun delete(id: Long): Try<Unit> = guard {
        if (dao.delete(id) > 0) Try.Success(Unit) else Try.Failure(AppError.NotFound)
    }

    /** Converts database exceptions to [AppError.Storage]; cancellation is rethrown. */
    private inline fun <T> guard(block: () -> Try<T>): Try<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteException) {
        Try.Failure(AppError.Storage(e.message ?: "database error"))
    }
}
