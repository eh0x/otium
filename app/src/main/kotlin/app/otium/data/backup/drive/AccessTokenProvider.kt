package app.otium.data.backup.drive

import app.otium.domain.error.Try

/** Hands out the OAuth token that lets the app use the user's Drive app data folder. */
internal fun interface AccessTokenProvider {
    /** A token valid for a few minutes, or `AuthExpired` when the user has to sign in again. */
    suspend fun accessToken(): Try<String>

    /**
     * Forgets [token], which Drive refused, so the next [accessToken] asks Google for a fresh one
     * instead of handing back the same cached token.
     */
    suspend fun discard(token: String) = Unit

    /** Drive refused a fresh token too: the grant is gone, and the user has to connect again. */
    suspend fun revoked() = Unit
}
