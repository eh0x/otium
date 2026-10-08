package app.otium.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerLookupSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scheduler = WorkManagerLookupScheduler(context)

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    private fun planned(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(LookupRetryWorker.UNIQUE_NAME)
        .get()

    @Test
    fun `a lookup is planned for when the device is online`() {
        scheduler.schedule()

        val work = planned().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun `planning again keeps the one already planned`() {
        scheduler.schedule()
        val first = planned().single().id

        scheduler.schedule()

        assertEquals(first, planned().single().id)
    }
}
