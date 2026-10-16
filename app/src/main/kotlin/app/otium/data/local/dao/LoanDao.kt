package app.otium.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import app.otium.data.local.entity.LoanEntity
import app.otium.data.local.row.OpenLoanRow
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LoanDao {

    @Query("SELECT * FROM loan WHERE bookId = :bookId ORDER BY lentOn DESC, id DESC")
    abstract fun observeLoans(bookId: Long): Flow<List<LoanEntity>>

    @Query(
        "SELECT loan.*, book.title AS title, book.authors AS authors, " +
            "book.coverPath AS coverPath, book.coverSourceUrl AS coverSourceUrl " +
            "FROM loan JOIN book ON book.id = loan.bookId " +
            "WHERE loan.returnedOn IS NULL ORDER BY loan.lentOn, loan.id",
    )
    abstract fun observeOpenLoans(): Flow<List<OpenLoanRow>>

    @Query("SELECT * FROM loan WHERE id = :id")
    abstract suspend fun get(id: Long): LoanEntity?

    @Query("SELECT * FROM loan WHERE returnedOn IS NULL ORDER BY id")
    abstract suspend fun openLoans(): List<LoanEntity>

    @Query("SELECT COUNT(*) FROM loan WHERE bookId = :bookId AND returnedOn IS NULL")
    abstract suspend fun openCount(bookId: Long): Int

    /** Fails with a constraint error when the book does not exist. */
    @Insert
    abstract suspend fun insert(loan: LoanEntity): Long

    /**
     * Inserts [loan], or returns null when it is open and its book already has an open loan. The
     * check and the insert are one transaction, as Room cannot declare a unique index over open
     * loans only.
     */
    @Transaction
    open suspend fun insertUnlessOpen(loan: LoanEntity): Long? =
        if (loan.returnedOn == null && openCount(loan.bookId) > 0) null else insert(loan)

    /** The number of rows changed: 0 when the loan does not exist. */
    @Query(
        "UPDATE loan SET counterparty = :counterparty, remindOn = :remindOn, " +
            "returnedOn = :returnedOn WHERE id = :id",
    )
    abstract suspend fun updateContent(
        id: Long,
        counterparty: String,
        remindOn: Long?,
        returnedOn: Long?,
    ): Int

    /** The number of rows deleted: 0 when the loan does not exist. */
    @Query("DELETE FROM loan WHERE id = :id")
    abstract suspend fun delete(id: Long): Int
}
