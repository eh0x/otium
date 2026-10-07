package app.otium.domain.model

/** Whether the app may use the user's Drive app data folder. */
sealed interface DriveAuthState {
    data object SignedOut : DriveAuthState

    /** Access was granted; [account] is the Google account, when it is known. */
    data class SignedIn(val account: String?) : DriveAuthState

    /** Access was granted once but has run out, so the user must sign in again. */
    data object Expired : DriveAuthState
}
