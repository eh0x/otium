package app.otium.ui.reading

import androidx.compose.runtime.Immutable
import app.otium.domain.error.AppError
import app.otium.domain.model.BookSummary

/** The Reading tab: the books on the desk and the last few finished. */
@Immutable
data class ReadingUiState(
    val isLoading: Boolean = true,
    /** Books in progress, most recently touched first. */
    val open: List<BookSummary> = emptyList(),
    /** The most recently finished books, newest first. */
    val finished: List<BookSummary> = emptyList(),
    val error: AppError? = null,
)

/** Everything the Reading tab can ask for. Defaults do nothing, which keeps tests short. */
@Immutable
data class ReadingActions(
    val onOpenBook: (Long) -> Unit = {},
    val onAddPages: (BookSummary) -> Unit = {},
    val onErrorShown: () -> Unit = {},
)
