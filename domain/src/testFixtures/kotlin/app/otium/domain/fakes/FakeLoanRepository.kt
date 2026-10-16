package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Loan
import app.otium.domain.model.OpenLoan
import app.otium.domain.repo.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [LoanRepository] for use case tests. Only the books [knownBooks] names exist, as a
 * foreign key would enforce in Room, and a book has one open loan at a time.
 */
class FakeLoanRepository(private val knownBooks: () -> Set<Long>) : LoanRepository {

    constructor(knownBooks: Set<Long> = emptySet()) : this({ knownBooks })

    /** The loans of the books in [books], as that repository holds them now. */
    constructor(books: FakeBookRepository) : this({ books.all.map { it.id }.toSet() })

    private val loans = MutableStateFlow<Map<Long, Loan>>(emptyMap())
    private var nextId = 1L

    /** Every loan, in the order they were added. */
    val all: List<Loan> get() = loans.value.values.toList()

    /** A book's loans, newest first, as a snapshot. */
    fun of(bookId: Long): List<Loan> = loans.value.values.filter { it.bookId == bookId }
        .sortedWith(Loan.NEWEST_FIRST)

    override fun observeLoans(bookId: Long): Flow<List<Loan>> = loans.map { of(bookId) }

    override fun observeOpenLoans(): Flow<List<OpenLoan>> = loans.map { map ->
        map.values.filter { it.isOpen }.sortedWith(compareBy({ it.lentOn }, { it.id })).map {
            OpenLoan(it, "Book ${it.bookId}", "Author", coverPath = null, coverSourceUrl = null)
        }
    }

    override suspend fun getLoan(id: Long): Try<Loan> =
        loans.value[id]?.let { Try.Success(it) } ?: Try.Failure(AppError.NotFound)

    override suspend fun openLoans(): Try<List<Loan>> =
        Try.Success(loans.value.values.filter { it.isOpen })

    override suspend fun add(loan: Loan): Try<Long> {
        if (loan.bookId !in knownBooks()) return Try.Failure(AppError.NotFound)
        if (loan.isOpen && of(loan.bookId).any { it.isOpen }) {
            return Try.Failure(AppError.AlreadyOnLoan)
        }
        val id = nextId++
        loans.value += (id to loan.copy(id = id))
        return Try.Success(id)
    }

    override suspend fun update(loan: Loan): Try<Unit> {
        val current = loans.value[loan.id] ?: return Try.Failure(AppError.NotFound)
        loans.value += (
            loan.id to current.copy(
                counterparty = loan.counterparty,
                remindOn = loan.remindOn,
                returnedOn = loan.returnedOn,
            )
            )
        return Try.Success(Unit)
    }

    override suspend fun delete(id: Long): Try<Unit> {
        if (id !in loans.value) return Try.Failure(AppError.NotFound)
        loans.value -= id
        return Try.Success(Unit)
    }
}
