package app.otium.ui.library

import androidx.compose.runtime.Immutable
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortOrder
import app.otium.ui.add.AddMethod

/**
 * Everything the library screen can ask for, in one stable holder so the stateless content
 * composable has one parameter for events instead of a dozen lambdas. Defaults do nothing,
 * which keeps tests and previews short.
 */
@Immutable
data class LibraryActions(
    val onQueryChange: (String) -> Unit = {},
    val onStatusSelected: (BookStatus?) -> Unit = {},
    val onCategoryToggled: (String) -> Unit = {},
    val onTagToggled: (String) -> Unit = {},
    val onSortSelected: (SortOrder) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onViewModeSelected: (LibraryViewMode) -> Unit = {},
    val onOpenBook: (Long) -> Unit = {},
    /** The user picked a way to add a book in the add sheet. */
    val onAddBook: (AddMethod) -> Unit = {},
    val onSeedDebugData: () -> Unit = {},
    val onOpenCatalog: () -> Unit = {},
)
