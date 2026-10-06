package app.otium.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.BookSummary
import app.otium.ui.common.rememberCoverPainter
import app.otium.ui.components.BookCover
import app.otium.ui.components.OtiumProgressBar
import app.otium.ui.components.ShelfBoard
import app.otium.ui.components.StatusChip
import app.otium.ui.components.labelRes

private val LIST_COVER_WIDTH = 56.dp

/** Read aloud as one item: "Dune by Frank Herbert", instead of its parts one by one. */
@Composable
private fun Modifier.bookSemantics(book: BookSummary, onOpen: () -> Unit): Modifier {
    val description = stringResource(R.string.book_open_description, book.title, book.authors)
    val status = stringResource(book.status.labelRes())
    val state = if (book.totalPages > 0) {
        stringResource(R.string.book_state_description, status, book.currentPage, book.totalPages)
    } else {
        status
    }
    return clickable(onClick = onOpen).clearAndSetSemantics {
        contentDescription = description
        stateDescription = state
        role = Role.Button
        onClick {
            onOpen()
            true
        }
    }
}

/** One book standing on its own piece of shelf: cover, title, author, and a thin progress bar. */
@Composable
fun ShelfBook(book: BookSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bookSemantics(book, onOpen)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BookCover(
                title = book.title,
                painter = rememberCoverPainter(book.coverPath),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                // Two lines always, so every board in a row sits at the same height.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.authors,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.totalPages > 0) {
                OtiumProgressBar(progress = book.currentPage.toFloat() / book.totalPages)
            }
        }
        ShelfBoard(modifier = Modifier.padding(top = 8.dp))
    }
}

/** A denser row for list mode: small cover, title and author, status, and progress. */
@Composable
fun BookListItem(book: BookSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bookSemantics(book, onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            title = book.title,
            painter = rememberCoverPainter(book.coverPath),
            modifier = Modifier.width(LIST_COVER_WIDTH),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.authors,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusChip(status = book.status)
            if (book.totalPages > 0) {
                OtiumProgressBar(progress = book.currentPage.toFloat() / book.totalPages)
            }
        }
    }
}
