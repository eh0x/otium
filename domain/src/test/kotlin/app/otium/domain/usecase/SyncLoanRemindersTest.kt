package app.otium.domain.usecase

import app.otium.domain.fakes.FakeLoanReminderScheduler
import app.otium.domain.fakes.FakeLoanRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncLoanRemindersTest {

    private val today = 20_000L
    private val repo = FakeLoanRepository(knownBooks = setOf(1L, 2L, 3L, 4L))
    private val reminders = FakeLoanReminderScheduler()
    private val sync = SyncLoanReminders(repo, reminders, FixedClock(today * 86_400_000L))

    private fun loan(bookId: Long, remindOn: Long?, returnedOn: Long? = null) =
        Loan(0L, bookId, "Sam", LoanDirection.LENT, today - 10, remindOn, returnedOn)

    @Test
    fun `plans open loans whose reminder is today or ahead and nothing else`() = runTest {
        repo.add(loan(1L, remindOn = today + 3))
        repo.add(loan(2L, remindOn = today - 1))
        repo.add(loan(3L, remindOn = null))
        repo.add(loan(4L, remindOn = today + 5, returnedOn = today - 2))

        sync()

        assertEquals(mapOf(1L to today + 3), reminders.planned)
    }

    @Test
    fun `does nothing when the ledger is empty`() = runTest {
        sync()

        assertEquals(emptyMap<Long, Long>(), reminders.planned)
    }
}
