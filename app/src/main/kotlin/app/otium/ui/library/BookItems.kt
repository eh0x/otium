package app.otium.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.ui.common.rememberCoverPainter
import app.otium.ui.components.BookCover
import app.otium.ui.components.OtiumProgressBar
import app.otium.ui.components.StatusGlyph
import app.otium.ui.components.hairlineBottom
import app.otium.ui.components.labelRes
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors

private val SHELF_BOOK_WIDTH = 96.dp
private val INDEX_COVER_WIDTH = 46.dp
private const val SHELF_TITLE_LINES = 3

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

private val BookSummary.progress: Float
    get() = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f

/**
 * A book on a shelf: cover, title, and a thin progress rule. The rule's track shows only while
 * the book is being read; a finished book shows a full rule, an unread one none.
 */
@Composable
fun ShelfBook(book: BookSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(SHELF_BOOK_WIDTH)
            .bookSemantics(book, onOpen),
    ) {
        BookCover(
            title = book.title,
            authors = book.authors,
            painter = rememberCoverPainter(book.coverPath),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall.copy(lineBreak = LineBreak.Heading),
            maxLines = SHELF_TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(modifier = Modifier.padding(top = 6.dp).height(2.dp)) {
            if (book.status != BookStatus.NOT_STARTED) {
                OtiumProgressBar(
                    progress = book.progress,
                    showTrack = book.status == BookStatus.IN_PROGRESS,
                )
            }
        }
    }
}

/**
 * A row of the index: cover, title, author, status (with the page while reading) and, at the
 * right edge, the book's place in the list, like an accession number.
 */
@Composable
fun IndexRow(book: BookSummary, position: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.otiumColors.muted
    val small = MaterialTheme.typography.bodySmall.copy(
        fontSize = 11.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .hairlineBottom(MaterialTheme.otiumColors.divider)
            .bookSemantics(book, onOpen)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            title = book.title,
            authors = book.authors,
            painter = rememberCoverPainter(book.coverPath),
            modifier = Modifier.width(INDEX_COVER_WIDTH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.authors,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IndexStatus(book = book, style = small, modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            text = stringResource(R.string.library_accession, position),
            style = small,
            color = muted,
        )
    }
}

/** Status glyph and label, with the page while the book is being read: "Reading · p. 12/300". */
@Composable
private fun IndexStatus(book: BookSummary, style: TextStyle, modifier: Modifier = Modifier) {
    val status = stringResource(book.status.labelRes())
    val label = if (book.status == BookStatus.IN_PROGRESS) {
        val page = stringResource(R.string.library_page_short, book.currentPage, book.totalPages)
        stringResource(R.string.dot_joined, status, page)
    } else {
        status
    }
    StatusGlyph(status = book.status, label = label, labelStyle = style, modifier = modifier)
}
