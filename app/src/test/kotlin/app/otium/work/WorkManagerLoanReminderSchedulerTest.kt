package app.otium.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerLoanReminderSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val day = LocalDate.of(2026, 11, 12)
    private val zone = ZoneId.systemDefault()
    private val now = day.minusDays(2).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
    private val scheduler = WorkManagerLoanReminderScheduler(context) { now }

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    private fun planned(loanId: Long): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(LoanReminderWorker.uniqueName(loanId))
        .get()

    @Test
    fun `a reminder is planned for each loan`() {
        scheduler.schedule(1L, day.toEpochDay())
        scheduler.schedule(2L, day.toEpochDay() + 1)

        assertEquals(WorkInfo.State.ENQUEUED, planned(1L).single().state)
        assertEquals(WorkInfo.State.ENQUEUED, planned(2L).single().state)
    }

    @Test
    fun `planning a loan again replaces its reminder`() {
        scheduler.schedule(1L, day.toEpochDay())
        val first = planned(1L).single().id

        scheduler.schedule(1L, day.toEpochDay() + 5)

        val all = planned(1L)
        assertTrue(all.single { it.state == WorkInfo.State.ENQUEUED }.id != first)
        assertTrue(all.none { it.id == first && it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `cancelling drops the reminder`() {
        scheduler.schedule(1L, day.toEpochDay())

        scheduler.cancel(1L)

        assertTrue(planned(1L).none { it.state == WorkInfo.State.ENQUEUED })
    }
}
