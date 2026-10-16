package app.otium.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.otium.data.cover.CoverFiles
import app.otium.data.repo.RoomBookRepository
import app.otium.data.repo.RoomLoanRepository
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoanRepositoryTest {

    private lateinit var db: OtiumDatabase
    private lateinit var books: RoomBookRepository
    private lateinit var loans: RoomLoanRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtiumDatabase::class.java,
        ).allowMainThreadQueries().build()
        val covers = CoverFiles(File("covers"))
        books = RoomBookRepository(db.bookDao(), covers, Dispatchers.Unconfined)
        loans = RoomLoanRepository(db.loanDao(), covers)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addBook(title: String = "Title"): Long =
        (books.add(testBook(title = title, authors = listOf("Patrick Rothfuss"))) as Try.Success)
            .value

    private fun loan(
        bookId: Long,
        name: String = "Sam",
        direction: LoanDirection = LoanDirection.LENT,
        lentOn: Long = 100L,
        remindOn: Long? = null,
        returnedOn: Long? = null,
    ) = Loan(0L, bookId, name, direction, lentOn, remindOn, returnedOn)

    private suspend fun add(loan: Loan): Long = (loans.add(loan) as Try.Success).value

    @Test
    fun `a loan is stored with everything it holds`() = runTest {
        val bookId = addBook()
        val id = add(loan(bookId, "Ana", LoanDirection.BORROWED, lentOn = 5L, remindOn = 9L))

        assertEquals(
            Loan(id, bookId, "Ana", LoanDirection.BORROWED, lentOn = 5L, remindOn = 9L),
            loans.observeLoans(bookId).first().single(),
        )
        assertEquals(Try.Success(loans.observeLoans(bookId).first().single()), loans.getLoan(id))
    }

    @Test
    fun `a book's loans come newest first`() = runTest {
        val bookId = addBook()
        add(loan(bookId, "Old", lentOn = 1L, returnedOn = 2L))
        add(loan(bookId, "New", lentOn = 8L))

        assertEquals(
            listOf("New", "Old"),
            loans.observeLoans(bookId).first().map {
                it.counterparty
            },
        )
    }

    @Test
    fun `a book with an open loan cannot take a second, but history is unlimited`() = runTest {
        val bookId = addBook()
        add(loan(bookId, "Sam"))

        assertEquals(Try.Failure(AppError.AlreadyOnLoan), loans.add(loan(bookId, "Ana")))
        add(loan(bookId, "Old one", lentOn = 1L, returnedOn = 2L))
        add(loan(bookId, "Older one", lentOn = 0L, returnedOn = 1L))

        assertEquals(3, loans.observeLoans(bookId).first().size)
    }

    @Test
    fun `a loan on a book that does not exist fails with not found`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), loans.add(loan(bookId = 404L)))
    }

    @Test
    fun `update replaces name, reminder and return but not the book or the day it began`() =
        runTest {
            val bookId = addBook()
            val id = add(loan(bookId, "Sam", lentOn = 5L, remindOn = 9L))

            val result = loans.update(
                Loan(
                    id,
                    999L,
                    "Samuel",
                    LoanDirection.BORROWED,
                    77L,
                    remindOn = null,
                    returnedOn = 12L,
                ),
            )

            assertEquals(Try.Success(Unit), result)
            assertEquals(
                Loan(
                    id,
                    bookId,
                    "Samuel",
                    LoanDirection.LENT,
                    5L,
                    remindOn = null,
                    returnedOn = 12L,
                ),
                loans.observeLoans(bookId).first().single(),
            )
        }

    @Test
    fun `update of a loan that does not exist fails with not found`() = runTest {
        assertEquals(
            Try.Failure(AppError.NotFound),
            loans.update(loan(addBook()).copy(id = 404L)),
        )
    }

    @Test
    fun `open loans list the open ones with their book, oldest first`() = runTest {
        val first = addBook("The Name of the Wind")
        val second = addBook("Dune")
        val third = addBook("Returned")
        add(loan(second, "Ana", lentOn = 9L))
        add(loan(first, "Sam", lentOn = 3L, remindOn = 20L))
        add(loan(third, "Zed", lentOn = 1L, returnedOn = 2L))

        val open = loans.observeOpenLoans().first()

        assertEquals(listOf("The Name of the Wind", "Dune"), open.map { it.title })
        assertEquals("Patrick Rothfuss", open.first().authors)
        assertEquals("Sam", open.first().loan.counterparty)
        // Planning reminders does not care about order; the rows come as they were added.
        assertEquals(
            listOf("Ana", "Sam"),
            (loans.openLoans() as Try.Success).value.map {
                it.counterparty
            },
        )
    }

    @Test
    fun `delete removes the loan, and a second delete fails with not found`() = runTest {
        val bookId = addBook()
        val id = add(loan(bookId))

        assertEquals(Try.Success(Unit), loans.delete(id))
        assertTrue(loans.observeLoans(bookId).first().isEmpty())
        assertEquals(Try.Failure(AppError.NotFound), loans.delete(id))
    }

    @Test
    fun `deleting a book deletes its loans and leaves other books' loans`() = runTest {
        val gone = addBook("Gone")
        val kept = addBook("Kept")
        add(loan(gone, "Goes"))
        add(loan(kept, "Stays"))

        books.delete(gone)

        assertTrue(loans.observeLoans(gone).first().isEmpty())
        assertEquals(listOf("Stays"), loans.observeLoans(kept).first().map { it.counterparty })
    }
}
