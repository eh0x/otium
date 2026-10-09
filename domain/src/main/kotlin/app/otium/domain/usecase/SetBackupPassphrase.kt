package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.port.BackupEncryption

/**
 * Turns backup encryption on, or changes its passphrase. The passphrase must be at least
 * [MIN_LENGTH] characters, not only spaces, and typed the same twice. Changing it affects new
 * backups only: the ones already in Drive still open with the passphrase they were made with.
 *
 * Fails with `InvalidInput` naming [FIELD_PASSPHRASE] or [FIELD_CONFIRMATION]. The arrays are
 * the caller's to clear.
 */
class SetBackupPassphrase(private val encryption: BackupEncryption) {

    suspend operator fun invoke(passphrase: CharArray, confirmation: CharArray): Try<Unit> = when {
        passphrase.size < MIN_LENGTH || passphrase.all(Char::isWhitespace) ->
            Try.Failure(AppError.InvalidInput(FIELD_PASSPHRASE))

        !passphrase.contentEquals(confirmation) ->
            Try.Failure(AppError.InvalidInput(FIELD_CONFIRMATION))

        else -> encryption.enable(passphrase)
    }

    companion object {
        const val MIN_LENGTH = 8
        const val FIELD_PASSPHRASE = "passphrase"
        const val FIELD_CONFIRMATION = "confirmation"
    }
}
