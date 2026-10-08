package app.otium.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.otium.domain.usecase.RunScheduledBackup
import app.otium.domain.usecase.ScheduledBackupOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one scheduled backup. The policy lives in [RunScheduledBackup]: this only turns its answer
 * into what WorkManager understands. A lost connection asks for a retry with backoff; a run that
 * cannot succeed ends as a success so the next period still happens, and the user is told once
 * enough of them have failed.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runScheduledBackup: RunScheduledBackup,
    private val notifier: BackupNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (val outcome = runScheduledBackup(runAttemptCount)) {
            ScheduledBackupOutcome.Done -> Result.success()

            ScheduledBackupOutcome.RetryLater -> Result.retry()

            is ScheduledBackupOutcome.Failed -> {
                if (outcome.notify) notifier.backupFailed(outcome.error)
                Result.success()
            }
        }

    companion object {
        const val UNIQUE_NAME = "scheduled-backup"
    }
}
