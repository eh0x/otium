package app.otium.ui.detail

import androidx.compose.runtime.Immutable
import app.otium.domain.error.AppError
import app.otium.domain.model.Book

/**
 * Everything the detail screen shows. A null [book] once [isLoading] is false means the book
 * is gone. Outcomes of actions are state, not one-off events: [deleted] tells the screen to
 * leave, [error] to show a message until it is dismissed.
 */
@Immutable
data class DetailUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val deleted: Boolean = false,
    val error: AppError? = null,
)
