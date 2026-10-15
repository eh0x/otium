package app.otium.ui.detail

import androidx.compose.runtime.Immutable
import app.otium.domain.error.AppError
import app.otium.domain.model.Book
import app.otium.domain.model.Note

/**
 * Everything the detail screen shows. A null [book] once [isLoading] is false means the book
 * is gone. Outcomes of actions are state, not one-off events: [deleted] tells the screen to
 * leave, [error] to show a message until it is dismissed. [notes] are the owner's notes on the
 * book in reading order.
 */
@Immutable
data class DetailUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val notes: List<Note> = emptyList(),
    val deleted: Boolean = false,
    val error: AppError? = null,
)
