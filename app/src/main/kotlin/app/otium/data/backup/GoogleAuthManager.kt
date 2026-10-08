package app.otium.data.backup

import android.accounts.Account
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.otium.auth.ConsentResult
import app.otium.auth.ConsentStep
import app.otium.auth.DriveConsent
import app.otium.data.backup.drive.AccessTokenProvider
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.error.flatMap
import app.otium.domain.model.DriveAuthState
import app.otium.domain.port.DriveAuth
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val GOOGLE_ACCOUNT_TYPE = "com.google"

/**
 * Access to the user's Drive app data folder through Google Identity Services authorization.
 * Otium has no sign-in of its own: Google shows its consent screen once, and afterwards a fresh
 * token is handed out silently for as long as the grant stands, which is what lets scheduled
 * backups run without the screen.
 *
 * Google does not say whether access was granted, so this remembers it: connected, the account's
 * email for display, and whether the last silent request found the grant gone (the user revoked
 * it, or it lapsed) and so needs the screen again.
 */
@Singleton
internal class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : AccessTokenProvider,
    DriveAuth,
    DriveConsent {

    private val client: AuthorizationClient get() = Identity.getAuthorizationClient(context)
    private val scopes = listOf(Scope(DRIVE_APPDATA_SCOPE))
    private val request = AuthorizationRequest.builder().setRequestedScopes(scopes).build()

    override val state: Flow<DriveAuthState> = dataStore.data.map { preferences ->
        when {
            preferences[CONNECTED] != true -> DriveAuthState.SignedOut
            preferences[EXPIRED] == true -> DriveAuthState.Expired
            else -> DriveAuthState.SignedIn(preferences[ACCOUNT])
        }
    }

    override suspend fun accessToken(): Try<String> = authorize().flatMap { result ->
        val token = result.accessToken
        if (result.hasResolution() || token == null) {
            markExpired()
            Try.Failure(AppError.AuthExpired)
        } else {
            Try.Success(token)
        }
    }

    override suspend fun begin(): Try<ConsentStep> = authorize().flatMap { result ->
        val pending = result.pendingIntent
        when {
            result.hasResolution() && pending != null -> Try.Success(ConsentStep.AskUser(pending))

            result.accessToken != null -> {
                remember(result)
                Try.Success(ConsentStep.Granted)
            }

            else -> Try.Failure(AppError.Unexpected("Google gave neither a token nor a screen"))
        }
    }

    override suspend fun finish(result: ConsentResult): Try<Unit> {
        val data = result.data ?: return Try.Success(Unit)
        return try {
            remember(client.getAuthorizationResultFromIntent(data))
            Try.Success(Unit)
        } catch (error: ApiException) {
            if (error.statusCode == CommonStatusCodes.CANCELED) {
                Try.Success(Unit)
            } else {
                Try.Failure(error.toAppError())
            }
        }
    }

    /** Revokes the grant when Google can be reached; either way, this device forgets it. */
    @Suppress("SwallowedException") // Offline sign-out still disconnects; the grant is harmless.
    override suspend fun signOut() {
        val account = dataStore.data.first()[ACCOUNT]
        try {
            val revoke = RevokeAccessRequest.builder().setScopes(scopes)
            account?.let { revoke.setAccount(Account(it, GOOGLE_ACCOUNT_TYPE)) }
            client.revokeAccess(revoke.build()).await()
        } catch (error: ApiException) {
            // Offline or already revoked.
        }
        dataStore.edit {
            it.remove(CONNECTED)
            it.remove(EXPIRED)
            it.remove(ACCOUNT)
        }
    }

    private suspend fun authorize(): Try<AuthorizationResult> = try {
        Try.Success(client.authorize(request).await())
    } catch (error: ApiException) {
        Try.Failure(error.toAppError())
    }

    // The result offers no other way to learn the account. Only the display needs it; without it
    // the screen says "Connected".
    @Suppress("DEPRECATION")
    private suspend fun remember(result: AuthorizationResult) {
        val email = result.toGoogleSignInAccount()?.email
        dataStore.edit {
            it[CONNECTED] = true
            it[EXPIRED] = false
            if (email != null) it[ACCOUNT] = email
        }
    }

    /** A grant that is gone matters only to a user who had one. */
    private suspend fun markExpired() {
        dataStore.edit { if (it[CONNECTED] == true) it[EXPIRED] = true }
    }

    private companion object {
        val CONNECTED = booleanPreferencesKey("drive_connected")
        val EXPIRED = booleanPreferencesKey("drive_expired")
        val ACCOUNT = stringPreferencesKey("drive_account")
    }
}

/** What a failed Google call means for the user. Codes that are bugs in setup stay visible. */
internal fun ApiException.toAppError(): AppError = when (statusCode) {
    CommonStatusCodes.NETWORK_ERROR -> AppError.Offline
    CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERRUPTED -> AppError.Timeout
    CommonStatusCodes.SIGN_IN_REQUIRED -> AppError.AuthExpired
    else -> AppError.Unexpected("Google authorization failed (code $statusCode)")
}
