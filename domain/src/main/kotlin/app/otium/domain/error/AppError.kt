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
    data class Storage(val message: String) : AppError
    data class Unexpected(val message: String) : AppError
}
