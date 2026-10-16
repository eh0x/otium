package app.otium.domain.error

/** Expected failure outcomes the UI handles explicitly. Framework exceptions never cross the data layer. */
sealed interface AppError {
    data object Offline : AppError
    data object RateLimited : AppError
    data object Timeout : AppError
    data object NotFound : AppError
    data class Duplicate(val existingBookId: Long) : AppError
    data class InvalidIsbn(val input: String) : AppError
    data class InvalidInput(val field: String) : AppError
    data object AuthExpired : AppError
    data object InsufficientDriveSpace : AppError
    data class IncompatibleBackup(val backupSchema: Int, val appSchema: Int) : AppError
    data object CorruptBackup : AppError
    data object NothingToBackUp : AppError

    /** The book already has an open loan; return it before lending or borrowing it again. */
    data object AlreadyOnLoan : AppError

    /** The file is not a library export Otium can read (Goodreads, StoryGraph or Otium). */
    data object UnknownFileFormat : AppError

    /** The backup is encrypted with a key this device does not hold; ask for its passphrase. */
    data object PassphraseRequired : AppError

    /** The passphrase given does not open this backup. */
    data object WrongPassphrase : AppError
    data class Storage(val message: String) : AppError
    data class Unexpected(val message: String) : AppError
}
