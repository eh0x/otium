package app.otium.domain.usecase

import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLoanRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.LoanReminder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrepareLoanReminderTest {

    private val today = 20_000L
    private val books = FakeBookRepository()
    private val loans = FakeLoanRepository(books)
    private val prepare = PrepareLoanReminder(loans, books, FixedClock(today * 86_400_000L))
    private val bookId = books.seed(book(title = "The Name of the Wind")).id

    private suspend fun open(remindOn: Long?, returnedOn: Long? = null): Long {
        val loan = Loan(0L, bookId, "Sam", LoanDirection.LENT, today - 20, remindOn, returnedOn)
        return (loans.add(loan) as app.otium.domain.error.Try.Success).value
    }

    @Test
    fun `an open loan whose day has come says who has the book and since when`() = runTest {
        val id = open(remindOn = today)

        assertEquals(
            LoanReminder(id, "The Name of the Wind", "Sam", LoanDirection.LENT, today - 20),
            prepare(id),
        )
    }

    @Test
    fun `a reminder that is late is still said`() = runTest {
        assertEquals("Sam", prepare(open(remindOn = today - 3))?.counterparty)
    }

    @Test
    fun `a reminder moved to a later day says nothing yet`() = runTest {
        assertNull(prepare(open(remindOn = today + 1)))
    }

    @Test
    fun `a returned loan, one without a reminder and one that is gone say nothing`() = runTest {
        assertNull(prepare(open(remindOn = today - 1, returnedOn = today - 1)))
        assertNull(prepare(open(remindOn = null)))
        assertNull(prepare(404L))
    }

    @Test
    fun `a loan whose book is gone says nothing`() = runTest {
        val id = open(remindOn = today)
        books.delete(bookId)

        assertNull(prepare(id))
    }
}
