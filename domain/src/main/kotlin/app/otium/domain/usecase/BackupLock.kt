package app.otium.domain.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lets one backup or restore run at a time, so a restore never replaces the library while a
 * backup is reading it. Backups, restores and scheduled backups must share one instance.
 */
class BackupLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
