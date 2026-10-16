package app.otium.work

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLoanRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.usecase.PrepareLoanReminder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LoanReminderWorkerTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val today = 20_000L
    private val books = FakeBookRepository()
    private val loans = FakeLoanRepository(books)
    private val bookId = books.seed(book(title = "The Name of the Wind")).id

    private fun run(loanId: Long?): ListenableWorker.Result {
        val prepare = PrepareLoanReminder(loans, books, FixedClock(today * 86_400_000L))
        return runBlocking {
            TestListenableWorkerBuilder<LoanReminderWorker>(application)
                .setInputData(
                    if (loanId == null) {
                        workDataOf()
                    } else {
                        workDataOf(LoanReminderWorker.KEY_LOAN_ID to loanId)
                    },
                )
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ) = LoanReminderWorker(
                            appContext,
                            workerParameters,
                            prepare,
                            LoanReminderNotifier(appContext),
                        )
                    },
                )
                .build()
                .doWork()
        }
    }

    private val notifications: NotificationManager
        get() = application.getSystemService(NotificationManager::class.java)

    private suspend fun lend(remindOn: Long?, direction: LoanDirection = LoanDirection.LENT) = (
        loans.add(Loan(0L, bookId, "Sam", direction, today - 20, remindOn)) as
            app.otium.domain.error.Try.Success
        ).value

    @Before
    fun allowNotifications() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `a loan whose day has come is announced once, quietly, with the name hidden when locked`() {
        val id = runBlocking { lend(remindOn = today) }

        assertEquals(ListenableWorker.Result.success(), run(id))

        val notification = notifications.activeNotifications.single().notification
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(text, "Sam" in text && "The Name of the Wind" in text)
        val publicText = notification.publicVersion.extras
            .getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(publicText, "Sam" !in publicText && "Wind" !in publicText)
        assertTrue("never alarming", "overdue" !in text.lowercase())
    }

    @Test
    fun `a borrowed book says it was borrowed`() {
        val id = runBlocking { lend(remindOn = today, direction = LoanDirection.BORROWED) }

        run(id)

        val title = notifications.activeNotifications.single().notification.extras
            .getCharSequence(Notification.EXTRA_TITLE).toString()
        assertTrue(title, "borrowed" in title)
    }

    @Test
    fun `a returned loan says nothing`() {
        val id = runBlocking { lend(remindOn = today) }
        runBlocking { loans.update(loans.all.single().copy(returnedOn = today)) }

        assertEquals(ListenableWorker.Result.success(), run(id))
        assertNull(notifications.activeNotifications.firstOrNull())
    }

    @Test
    fun `without the permission the run still succeeds and shows nothing`() {
        val id = runBlocking { lend(remindOn = today) }
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(ListenableWorker.Result.success(), run(id))
        assertNull(notifications.activeNotifications.firstOrNull())
    }

    @Test
    fun `work without a loan succeeds and says nothing`() {
        assertEquals(ListenableWorker.Result.success(), run(null))
        assertNull(notifications.activeNotifications.firstOrNull())
    }
}
