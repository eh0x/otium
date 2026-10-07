package app.otium.auth

import android.app.PendingIntent
import android.content.Intent
import app.otium.domain.error.Try
import app.otium.domain.port.DriveAuth

/** What the screen has to do next to give Otium access to the user's Drive app data folder. */
sealed interface ConsentStep {
    /** Access is in place; nothing more to ask. */
    data object Granted : ConsentStep

    /** The user must agree on Google's own screen. Launch [request] and report what it returns. */
    data class AskUser(val request: PendingIntent) : ConsentStep
}

/** What Google's consent screen handed back; [data] is null when the user backed out. */
class ConsentResult(val data: Intent?)

/**
 * [DriveAuth] plus the part that needs the screen. Asking for access takes an Activity result, so
 * the backup screen takes part in two steps: [begin] tells whether Google's screen is needed, and
 * [finish] records what it returned. The rest of the app only sees [DriveAuth].
 */
interface DriveConsent : DriveAuth {
    suspend fun begin(): Try<ConsentStep>

    /** A user who backed out is not an error: access simply stays as it was. */
    suspend fun finish(result: ConsentResult): Try<Unit>
}
