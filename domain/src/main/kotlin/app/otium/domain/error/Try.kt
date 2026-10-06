package app.otium.domain.error

/** Result type with a typed [AppError] failure, unlike kotlin.Result which erases the error type. */
sealed interface Try<out T> {
    data class Success<out T>(val value: T) : Try<T>
    data class Failure(val error: AppError) : Try<Nothing>
}

inline fun <T, R> Try<T>.map(transform: (T) -> R): Try<R> = when (this) {
    is Try.Success -> Try.Success(transform(value))
    is Try.Failure -> this
}

inline fun <T, R> Try<T>.flatMap(transform: (T) -> Try<R>): Try<R> = when (this) {
    is Try.Success -> transform(value)
    is Try.Failure -> this
}

inline fun <T, R> Try<T>.fold(onSuccess: (T) -> R, onFailure: (AppError) -> R): R = when (this) {
    is Try.Success -> onSuccess(value)
    is Try.Failure -> onFailure(error)
}

fun <T> Try<T>.getOrNull(): T? = (this as? Try.Success)?.value

fun <T> Try<T>.errorOrNull(): AppError? = (this as? Try.Failure)?.error
