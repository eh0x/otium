package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BackupFrequency
import app.otium.domain.port.BackupPreferences
import kotlinx.coroutines.flow.first

/** What the background worker should do after a scheduled backup attempt. */
sealed interface ScheduledBackupOutcome {
    /** Backed up, or nothing was to be done. */
    data object Done : ScheduledBackupOutcome

    /** Could not reach Drive; try again after a delay. */
    data object RetryLater : ScheduledBackupOutcome

    /** This run is over without a backup. [notify] is true once the user should hear of it. */
    data class Failed(val error: AppError, val notify: Boolean) : ScheduledBackupOutcome
}

/**
 * One attempt of a scheduled backup. A lost connection is retried within the run, up to
 * [MAX_ATTEMPTS]; anything the user has to fix, such as signing in again or freeing Drive space,
 * ends the run at once. Each run that ends without a backup counts, and the user is told only when
 * enough of them fail in a row (see [BackupFrequency.failedRunsBeforeNotice]), and then once.
 */
class RunScheduledBackup(
    private val runBackup: RunBackup,
    private val preferences: BackupPreferences,
) {
    /** [attempt] counts from 0 within one run, as WorkManager's run attempt count does. */
    suspend operator fun invoke(attempt: Int): ScheduledBackupOutcome {
        val settings = preferences.settings.first()
        if (!settings.autoBackup) return ScheduledBackupOutcome.Done
        return when (val result = runBackup()) {
            is Try.Success -> ScheduledBackupOutcome.Done
            is Try.Failure -> afterFailure(result.error, attempt, settings.frequency)
        }
    }

    private suspend fun afterFailure(
        error: AppError,
        attempt: Int,
        frequency: BackupFrequency,
    ): ScheduledBackupOutcome = when {
        error == AppError.NothingToBackUp -> ScheduledBackupOutcome.Done

        error.isTemporary() && attempt + 1 < MAX_ATTEMPTS -> ScheduledBackupOutcome.RetryLater

        else -> {
            val failedRuns = preferences.failedRuns() + 1
            preferences.setFailedRuns(failedRuns)
            ScheduledBackupOutcome.Failed(
                error,
                notify =
                    failedRuns == frequency.failedRunsBeforeNotice,
            )
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}

private fun AppError.isTemporary(): Boolean =
    this == AppError.Offline || this == AppError.Timeout || this == AppError.RateLimited
