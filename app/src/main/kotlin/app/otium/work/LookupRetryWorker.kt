package app.otium.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.otium.domain.error.Try
import app.otium.domain.usecase.RetryPendingLookups
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Looks up the books saved from an ISBN alone while offline. WorkManager runs it once the device
 * is online and backs off between tries. It stops after [MAX_ATTEMPTS]; books still waiting are
 * picked up by the next run, which saving another ISBN or starting the app schedules.
 */
@HiltWorker
class LookupRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val retryPendingLookups: RetryPendingLookups,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = retryPendingLookups()
        val done = result is Try.Success && result.value.remaining == 0
        return when {
            done -> Result.success()
            runAttemptCount >= MAX_ATTEMPTS -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "pending-lookups"
        const val MAX_ATTEMPTS = 10
    }
}
