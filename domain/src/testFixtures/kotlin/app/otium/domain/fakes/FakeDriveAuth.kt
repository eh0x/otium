package app.otium.domain.fakes

import app.otium.domain.model.DriveAuthState
import app.otium.domain.port.DriveAuth
import kotlinx.coroutines.flow.MutableStateFlow

/** Drive access held in memory; set [state] to sign in or out. */
class FakeDriveAuth(initial: DriveAuthState = DriveAuthState.SignedOut) : DriveAuth {
    override val state = MutableStateFlow(initial)

    override suspend fun signOut() {
        state.value = DriveAuthState.SignedOut
    }
}
