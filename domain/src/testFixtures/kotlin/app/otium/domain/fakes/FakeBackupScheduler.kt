package app.otium.domain.fakes

import app.otium.domain.model.BackupSettings
import app.otium.domain.port.BackupScheduler

class FakeBackupScheduler : BackupScheduler {
    /** The plan in force, or null when nothing is scheduled. */
    var scheduled: BackupSettings? = null
        private set

    override fun schedule(settings: BackupSettings) {
        scheduled = settings
    }

    override fun cancel() {
        scheduled = null
    }
}
