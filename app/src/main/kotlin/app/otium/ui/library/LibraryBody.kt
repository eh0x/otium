package app.otium.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryViewMode
import app.otium.ui.components.Hairline
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.hairlineBottom
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors

/** What fills the screen below the controls: the books, an empty-library prompt, or no results. */
@Composable
internal fun LibraryBody(state: LibraryUiState, actions: LibraryActions) {
    when (state.contentKind) {
        LibraryContentKind.LOADING -> Unit

        LibraryContentKind.EMPTY_LIBRARY -> EmptyMessage(
            title = stringResource(R.string.library_empty_title),
            message = stringResource(R.string.library_empty_message),
            actionLabel = stringResource(R.string.library_empty_action),
            onAction = actions.onOpenAddSheet,
        )

        LibraryContentKind.NO_RESULTS -> EmptyMessage(
            title = stringResource(R.string.library_no_results_title),
            message = stringResource(R.string.library_no_results_message),
            actionLabel = stringResource(R.string.filter_clear),
            onAction = actions.onClearFilters,
        )

        LibraryContentKind.BOOKS -> when (state.viewMode) {
            LibraryViewMode.SHELF -> Shelves(state, actions)
            LibraryViewMode.LIST -> Index(state, actions)
        }
    }
}

/** "On the desk" first, then one shelf per category. */
@Composable
private fun Shelves(state: LibraryUiState, actions: LibraryActions) {
    val unshelved = stringResource(R.string.library_unshelved)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 18.dp),
    ) {
        if (state.desk.isNotEmpty()) {
            item(key = "desk", contentType = "shelf") {
                ShelfSection(
                    name = stringResource(R.string.library_desk),
                    count = pluralStringResource(
                        R.plurals.library_desk_count,
                        state.desk.size,
                        state.desk.size,
                    ),
                    books = state.desk,
                    actions = actions,
                )
            }
        }
        items(items = state.shelves, key = { "shelf:${it.name}" }, contentType = { "shelf" }) {
            ShelfSection(
                name = it.name ?: unshelved,
                count = pluralStringResource(
                    R.plurals.library_book_count,
                    it.books.size,
                    it.books.size,
                ),
                books = it.books,
                actions = actions,
            )
        }
    }
}

@Composable
private fun ShelfSection(
    name: String,
    count: String,
    books: List<BookSummary>,
    actions: LibraryActions,
) {
    val divider = MaterialTheme.otiumColors.divider
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, top = 6.dp, end = 22.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
                    .semantics { heading() },
            )
            Text(
                text = count,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
                color = MaterialTheme.otiumColors.muted,
                modifier = Modifier.alignByBaseline(),
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineBottom(divider),
            contentPadding = PaddingValues(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(items = books, key = { it.id }, contentType = { "book" }) { book ->
                ShelfBook(book = book, onOpen = { actions.onOpenBook(book.id) })
            }
        }
        // The shelf's edge: a second, shorter rule under the first.
        Box(
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .height(5.dp)
                .hairlineBottom(divider),
        )
    }
}

/** The index: rows in sort order, under letter headings when sorted by title. */
@Composable
private fun Index(state: LibraryUiState, actions: LibraryActions) {
    val positions = remember(state.books) {
        state.books.withIndex().associate { (index, book) -> book.id to index + 1 }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 18.dp),
    ) {
        state.index.forEach { group ->
            group.letter?.let { letter ->
                item(key = "letter:$letter", contentType = "letter") { LetterHeading(letter) }
            }
            items(items = group.books, key = { it.id }, contentType = { "book" }) { book ->
                IndexRow(
                    book = book,
                    position = positions.getValue(book.id),
                    onOpen = { actions.onOpenBook(book.id) },
                )
            }
        }
    }
}

@Composable
private fun LetterHeading(letter: String) {
    Row(
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Hairline(modifier = Modifier.weight(1f))
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
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 70.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 30.sp,
                letterSpacing = 0.em,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.otiumColors.muted,
            textAlign = TextAlign.Center,
        )
        OtiumButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 8.dp))
    }
}
