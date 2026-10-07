package app.otium.domain.usecase

import app.otium.domain.model.BackupSettings
import app.otium.domain.port.BackupPreferences
import app.otium.domain.port.BackupScheduler

/** Saves the user's scheduled backup choices and makes the schedule match them. */
class ConfigureAutoBackup(
    private val preferences: BackupPreferences,
    private val scheduler: BackupScheduler,
) {
    suspend operator fun invoke(settings: BackupSettings) {
        preferences.saveSettings(settings)
        if (settings.autoBackup) scheduler.schedule(settings) else scheduler.cancel()
    }
}
