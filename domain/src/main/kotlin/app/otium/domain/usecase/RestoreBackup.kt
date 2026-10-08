package app.otium.domain.usecase

import app.otium.domain.error.Try
import app.otium.domain.model.RestorePreview
import app.otium.domain.port.LibraryRestorer
import app.otium.domain.port.LookupScheduler

/**
 * The second half of a restore, run after the user has confirmed: replaces the library with the
 * backup [PrepareRestore] checked. The restorer changes everything or nothing, so a failure leaves
 * the library as it was and the downloaded file in place for another try.
 */
class RestoreBackup(
    private val restorer: LibraryRestorer,
    private val lookups: LookupScheduler,
    private val lock: BackupLock,
) {
    suspend operator fun invoke(preview: RestorePreview): Try<Unit> = lock.withLock {
        restorer.restore(preview.file).also { result ->
            if (result is Try.Success) {
                preview.discard()
                // Books that were still waiting for their details when backed up resume the lookup.
                lookups.schedule()
            }
        }
    }
}
