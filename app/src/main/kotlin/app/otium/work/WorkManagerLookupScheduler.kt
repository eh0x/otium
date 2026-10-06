package app.otium.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.otium.domain.port.LookupScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val BACKOFF_SECONDS = 30L

/**
 * Plans [LookupRetryWorker] as unique work that waits for a network connection and backs off
 * exponentially. A run already planned is kept, so scheduling often costs nothing.
 */
class WorkManagerLookupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : LookupScheduler {

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<LookupRetryWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(LookupRetryWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
