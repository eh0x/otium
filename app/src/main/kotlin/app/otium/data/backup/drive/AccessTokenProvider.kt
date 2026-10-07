package app.otium.data.backup.drive

import app.otium.domain.error.Try

/** Hands out the OAuth token that lets the app use the user's Drive app data folder. */
internal fun interface AccessTokenProvider {
    /** A token valid for a few minutes, or `AuthExpired` when the user has to sign in again. */
    suspend fun accessToken(): Try<String>
}
