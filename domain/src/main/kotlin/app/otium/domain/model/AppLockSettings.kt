package app.otium.domain.model

/**
 * How long Otium may stay in the background before it asks to be unlocked again. There is no
 * "immediately": picking a file, taking a cover photo or Google's consent screen all leave the app
 * for a moment, and would each ask again.
 */
enum class LockTimeout(val millis: Long) {
    ONE_MINUTE(millis = 60_000L),
    FIVE_MINUTES(millis = 300_000L),
}

/** The owner's app lock choice. Off by default; per device, never part of a backup. */
data class AppLockSettings(
    val enabled: Boolean = false,
    val timeout: LockTimeout = LockTimeout.ONE_MINUTE,
)
