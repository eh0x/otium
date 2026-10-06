package app.otium.ui.add

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource

/** Where the add flow goes next. ViewModels hold it as state until the screen has navigated. */
sealed interface AddDestination {
    /** The confirm screen, filled with [candidate]. */
    data class Confirm(val candidate: BookCandidate) : AddDestination

    /** A book already in the library: the one the ISBN belongs to, or one just saved. */
    data class Book(val bookId: Long) : AddDestination
}

/**
 * What an ISBN lookup means for the add flow. A found book goes to the confirm screen, a book
 * already owned opens instead, and an ISBN nobody knows opens an empty confirm screen with the
 * ISBN filled in. Anything else is an error for the screen to show.
 */
internal fun Try<BookCandidate>.toDestination(isbn13: String): Try<AddDestination> = when (this) {
    is Try.Success -> Try.Success(AddDestination.Confirm(value))

    is Try.Failure -> when (val failure = error) {
        is AppError.Duplicate -> Try.Success(AddDestination.Book(failure.existingBookId))
        AppError.NotFound -> Try.Success(AddDestination.Confirm(emptyCandidate(isbn13)))
        else -> this
    }
}

/** An empty confirm form that keeps the ISBN the user already gave. */
internal fun emptyCandidate(isbn13: String?) =
    BookCandidate(isbn13 = isbn13, source = BookSource.MANUAL)

/** Errors after which keeping the ISBN to look up later makes sense: the services were out of reach. */
internal val AppError.isTransient: Boolean
    get() = this == AppError.Offline || this == AppError.Timeout || this == AppError.RateLimited
