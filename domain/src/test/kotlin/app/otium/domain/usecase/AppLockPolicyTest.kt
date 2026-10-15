package app.otium.domain.usecase

import app.otium.domain.model.AppLockSettings
import app.otium.domain.model.LockTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {

    private val on = AppLockSettings(enabled = true, timeout = LockTimeout.ONE_MINUTE)

    @Test
    fun `nothing locks while the app lock is off`() {
        val off = AppLockSettings()

        assertFalse(AppLockPolicy.shouldLock(off, backgroundedAt = null, now = 0L))
        assertFalse(AppLockPolicy.shouldLock(off, backgroundedAt = 0L, now = 1_000_000_000L))
    }

    @Test
    fun `every start locks`() {
        assertTrue(AppLockPolicy.shouldLock(on, backgroundedAt = null, now = 5L))
    }

    @Test
    fun `a short time away does not lock`() {
        assertFalse(AppLockPolicy.shouldLock(on, backgroundedAt = 1_000L, now = 60_999L))
    }

    @Test
    fun `the timeout itself locks`() {
        assertTrue(AppLockPolicy.shouldLock(on, backgroundedAt = 1_000L, now = 61_000L))
    }

    @Test
    fun `the longer timeout is honoured`() {
        val five = on.copy(timeout = LockTimeout.FIVE_MINUTES)

        assertFalse(AppLockPolicy.shouldLock(five, backgroundedAt = 0L, now = 299_999L))
        assertTrue(AppLockPolicy.shouldLock(five, backgroundedAt = 0L, now = 300_000L))
    }

    @Test
    fun `a clock that runs backwards locks, as the safe answer`() {
        assertTrue(AppLockPolicy.shouldLock(on, backgroundedAt = 10_000L, now = 5_000L))
    }

    @Test
    fun `the lock is off by default and waits one minute once on`() {
        assertFalse(AppLockSettings().enabled)
        assertEquals(LockTimeout.ONE_MINUTE, AppLockSettings().timeout)
    }
}
