package app.otium.domain.port

import app.otium.domain.model.DriveAuthState
import kotlinx.coroutines.flow.Flow

/**
 * The user's access to their Drive app data folder. Asking for access needs the screen, so it is
 * done by the UI layer; this is what the rest of the app needs to know and do.
 */
interface DriveAuth {
    val state: Flow<DriveAuthState>

    /** Forgets the granted access. Backups already in Drive stay. */
    suspend fun signOut()
}
