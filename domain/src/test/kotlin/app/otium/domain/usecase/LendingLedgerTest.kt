package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeLoanReminderScheduler
import app.otium.domain.fakes.FakeLoanRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LendingLedgerTest {

    private val today = 20_000L
    private val repo = FakeLoanRepository(knownBooks = setOf(7L, 8L))
    private val reminders = FakeLoanReminderScheduler()
    private val clock = FixedClock(now = today * 86_400_000L + 3_600_000L)
    private val ledger = LendingLedger(repo, reminders, clock)

    private suspend fun lend(
        bookId: Long = 7L,
        name: String = "Sam",
        lentOn: Long = today,
        remindOn: Long? = null,
    ) = ledger.open(bookId, LoanDirection.LENT, name, lentOn, remindOn)

    @Test
    fun `lends a book to a name typed by hand`() = runTest {
        val result = lend(name = "  Sam ", lentOn = today - 3)

        assertEquals(Try.Success(1L), result)
        assertEquals(
            Loan(1L, 7L, "Sam", LoanDirection.LENT, lentOn = today - 3),
            repo.all.single(),
        )
    }

    @Test
    fun `records a borrowed book in the other direction`() = runTest {
        ledger.open(7L, LoanDirection.BORROWED, "Ana", today, null)

        assertEquals(LoanDirection.BORROWED, repo.all.single().direction)
    }

    @Test
    fun `a blank or too long name is refused and nothing is stored`() = runTest {
        assertEquals(Try.Failure(AppError.InvalidInput("name")), lend(name = " "))
        assertEquals(Try.Failure(AppError.InvalidInput("name")), lend(name = "x".repeat(101)))
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `a book cannot go out before it is lent or from a day to come`() = runTest {
        assertEquals(Try.Failure(AppError.InvalidInput("lentOn")), lend(lentOn = today + 1))
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `a reminder must be after today and after the loan began`() = runTest {
        assertEquals(Try.Failure(AppError.InvalidInput("remindOn")), lend(remindOn = today))
        assertEquals(
            Try.Failure(AppError.InvalidInput("remindOn")),
            lend(lentOn = today - 5, remindOn = today - 2),
        )
        assertTrue(reminders.planned.isEmpty())
    }

    @Test
    fun `a reminder is planned for the day chosen`() = runTest {
        lend(remindOn = today + 14)

        assertEquals(mapOf(1L to today + 14), reminders.planned)
    }

    @Test
    fun `a book out cannot be lent again until it is returned`() = runTest {
        lend()

        assertEquals(Try.Failure(AppError.AlreadyOnLoan), lend(name = "Ana"))

        ledger.markReturned(1L)
        assertEquals(Try.Success(2L), lend(name = "Ana"))
    }

    @Test
    fun `a book that does not exist fails with not found and plans nothing`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), lend(bookId = 404L, remindOn = today + 3))
        assertTrue(reminders.planned.isEmpty())
    }

    @Test
    fun `returning keeps the loan as history and drops its reminder`() = runTest {
        lend(lentOn = today - 4, remindOn = today + 7)

        assertEquals(Try.Success(Unit), ledger.markReturned(1L))

        assertEquals(today, repo.all.single().returnedOn)
        assertTrue(reminders.planned.isEmpty())
    }

    @Test
    fun `returning twice changes nothing`() = runTest {
        lend()
        ledger.markReturned(1L)
        clock.now += 86_400_000L

        assertEquals(Try.Success(Unit), ledger.markReturned(1L))
        assertEquals(today, repo.all.single().returnedOn)
    }

    @Test
    fun `returning a loan that does not exist fails with not found`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), ledger.markReturned(404L))
    }

    @Test
    fun `a reminder can be moved and cleared`() = runTest {
        lend(remindOn = today + 7)

        assertEquals(Try.Success(Unit), ledger.changeReminder(1L, today + 30))
        assertEquals(mapOf(1L to today + 30), reminders.planned)

        assertEquals(Try.Success(Unit), ledger.changeReminder(1L, null))
        assertTrue(reminders.planned.isEmpty())
        assertEquals(null, repo.all.single().remindOn)
    }

    @Test
    fun `a reminder cannot be set on a returned loan or for a day gone`() = runTest {
        lend()
        assertEquals(
            Try.Failure(AppError.InvalidInput("remindOn")),
            ledger.changeReminder(1L, today),
        )

        ledger.markReturned(1L)
        assertEquals(
            Try.Failure(AppError.InvalidInput("loan")),
            ledger.changeReminder(1L, today + 5),
        )
    }

    @Test
    fun `removing a loan drops it and its reminder`() = runTest {
        lend(remindOn = today + 7)

        assertEquals(Try.Success(Unit), ledger.remove(1L))

        assertTrue(repo.all.isEmpty())
        assertTrue(reminders.planned.isEmpty())
        assertEquals(Try.Failure(AppError.NotFound), ledger.remove(1L))
    }
}
