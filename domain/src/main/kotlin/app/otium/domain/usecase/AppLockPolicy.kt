package app.otium.domain.usecase

import app.otium.domain.model.AppLockSettings

/**
 * When Otium asks to be unlocked: on every start, and on coming back after the app spent at least
 * the chosen timeout in the background. Times come from a clock that only moves forward (time
 * since boot), so changing the phone's clock cannot skip the lock; a time that still runs
 * backwards locks, as the safe answer.
 */
object AppLockPolicy {
    /**
     * Whether to lock now. [backgroundedAt] is when the app last went to the background, or null
     * when it is starting.
     */
    fun shouldLock(settings: AppLockSettings, backgroundedAt: Long?, now: Long): Boolean {
        if (!settings.enabled) return false
        if (backgroundedAt == null) return true
        val away = now - backgroundedAt
        return away < 0 || away >= settings.timeout.millis
    }
}
