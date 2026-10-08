package app.otium.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupSettings
import app.otium.domain.port.BackupScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val BACKOFF_MINUTES = 15L
private const val DAILY = 1L
private const val WEEKLY = 7L

/**
 * Plans [BackupWorker] as unique periodic work under the user's constraints. Planning again after
 * a settings change updates the one request in place, so there is never more than one.
 */
class WorkManagerBackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupScheduler {

    override fun schedule(settings: BackupSettings) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .setRequiresCharging(settings.chargingOnly)
            .build()
        val days = when (settings.frequency) {
            BackupFrequency.DAILY -> DAILY
            BackupFrequency.WEEKLY -> WEEKLY
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BackupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(BackupWorker.UNIQUE_NAME)
    }
}
