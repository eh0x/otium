package app.otium.domain.port

import app.otium.domain.model.BackupSettings

/** Arranges for the library to be backed up on a schedule, within the user's constraints. */
interface BackupScheduler {
    /** Plans scheduled backups as [settings] say, replacing any earlier plan. */
    fun schedule(settings: BackupSettings)

    /** Stops scheduled backups. */
    fun cancel()
}
