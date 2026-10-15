package app.otium.ui.applock

import androidx.compose.runtime.Immutable
import app.otium.applock.LockState

/**
 * What the gate shows. [promptRequest] grows each time the prompt should open by itself (when the
 * app locks, or comes back while locked); [unlockMessage] is why the system last refused.
 */
@Immutable
data class AppLockGateState(
    val state: LockState,
    val promptRequest: Int = 0,
    val turnedOffNotice: Boolean = false,
    val unlockMessage: String? = null,
)
