package app.otium.ui.common

import androidx.annotation.StringRes
import app.otium.R
import app.otium.domain.error.AppError

/**
 * The one place that turns a domain failure into words for the user. Technical details in the
 * error (messages, inputs) are never shown; the exhaustive `when` makes a new error type a
 * compile error until it has a message.
 */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Offline -> R.string.error_offline
    AppError.RateLimited -> R.string.error_rate_limited
    AppError.Timeout -> R.string.error_timeout
    AppError.NotFound -> R.string.error_not_found
    is AppError.Duplicate -> R.string.error_duplicate
    is AppError.InvalidIsbn -> R.string.error_invalid_isbn
    is AppError.InvalidInput -> R.string.error_invalid_input
    AppError.AuthExpired -> R.string.error_auth_expired
    AppError.InsufficientDriveSpace -> R.string.error_drive_space
    is AppError.IncompatibleBackup -> R.string.error_incompatible_backup
    is AppError.Storage -> R.string.error_storage
    is AppError.Unexpected -> R.string.error_unexpected
}
