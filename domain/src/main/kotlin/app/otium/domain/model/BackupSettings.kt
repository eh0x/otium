package app.otium.domain.model

/**
 * How often a scheduled backup runs. [failedRunsBeforeNotice] is how many runs in a row may fail
 * before the user is told: roughly the same few days of silence whichever the frequency.
 */
enum class BackupFrequency(val failedRunsBeforeNotice: Int) {
    DAILY(failedRunsBeforeNotice = 3),
    WEEKLY(failedRunsBeforeNotice = 2),
}

/** The user's choices for scheduled backups. A backup file is large, so Wi-Fi is the default. */
data class BackupSettings(
    val autoBackup: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
)
