package app.otium.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.LibraryViewMode

private val GRID_CELL_MIN_WIDTH = 112.dp
private val FAB_CLEARANCE = 96.dp

/** What fills the screen below the controls: the books, an empty-library prompt, or no results. */
@Composable
internal fun LibraryBody(state: LibraryUiState, actions: LibraryActions) {
    when (state.contentKind) {
        LibraryContentKind.LOADING -> Unit

        LibraryContentKind.EMPTY_LIBRARY -> EmptyMessage(
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
            actionLabel = stringResource(R.string.library_empty_action),
            onAction = actions.onAddBook,
        )

        LibraryContentKind.NO_RESULTS -> EmptyMessage(
            title = stringResource(R.string.library_no_results_title),
            message = stringResource(R.string.library_no_results_message),
            actionLabel = stringResource(R.string.filter_clear),
            onAction = actions.onClearFilters,
        )

        LibraryContentKind.BOOKS -> when (state.viewMode) {
            LibraryViewMode.SHELF -> ShelfGrid(state, actions)
            LibraryViewMode.LIST -> BookList(state, actions)
        }
    }
}

@Composable
private fun ShelfGrid(state: LibraryUiState, actions: LibraryActions) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_CELL_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = FAB_CLEARANCE,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = state.books, key = { it.id }, contentType = { "book" }) { book ->
            ShelfBook(book = book, onOpen = { actions.onOpenBook(book.id) })
        }
    }
}

@Composable
private fun BookList(state: LibraryUiState, actions: LibraryActions) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = FAB_CLEARANCE),
    ) {
        items(items = state.books, key = { it.id }, contentType = { "book" }) { book ->
            BookListItem(book = book, onOpen = { actions.onOpenBook(book.id) })
        }
    }
}

@Composable
private fun EmptyMessage(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
