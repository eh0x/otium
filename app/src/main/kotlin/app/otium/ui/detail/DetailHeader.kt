package app.otium.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.domain.model.Book
import app.otium.ui.common.rememberCoverPainter
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.PlatedCover
import app.otium.ui.components.StatusGlyph
import app.otium.ui.components.hairlineBottom
import app.otium.ui.components.isLargeFontScale
import app.otium.ui.components.labelRes
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors

private val PLATE_WIDTH = 122.dp
private const val DESCRIPTION_COLLAPSED_LINES = 4
private const val DESCRIPTION_LINE_HEIGHT = 1.62f

/**
 * The plated cover beside the status, title, author and "Collection · year". With very large
 * text the words go below the cover instead, so the title gets the full width.
 */
@Composable
internal fun DetailHeader(book: Book) {
    val authors = book.authors.joinToString(", ")
    val cover = @Composable {
        PlatedCover(
            title = book.title,
            authors = authors,
            painter = rememberCoverPainter(book.coverPath),
            modifier = Modifier.width(PLATE_WIDTH),
        )
    }
    if (isLargeFontScale()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            cover()
            HeaderText(book, authors)
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            cover()
            HeaderText(book, authors, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderText(book: Book, authors: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusGlyph(
            status = book.status,
            label = stringResource(book.status.labelRes()).uppercase(),
            labelStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.16.em),
            labelColor = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                lineBreak = LineBreak.Heading,
                hyphens = Hyphens.Auto,
            ),
            modifier = Modifier.semantics { heading() },
        )
        book.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
        if (authors.isNotBlank()) {
            Text(
                text = authors,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            )
        }
        headerMeta(book)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = MaterialTheme.otiumColors.muted,
            )
        }
    }
}

/** "Philosophy · 2003": the collection and the year, whichever are known. */
@Composable
private fun headerMeta(book: Book): String? {
    val collection = book.categories.firstOrNull()
    val year = book.publishedYear?.toString()
    return when {
        collection != null && year != null -> stringResource(R.string.dot_joined, collection, year)
        else -> collection ?: year
    }
}

/** The About tab: the description, then the facts. Marginalia is a later tab. */
@Composable
internal fun AboutTab(book: Book, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().hairlineBottom(MaterialTheme.otiumColors.divider)) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                Text(
                    text = stringResource(R.string.detail_about),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .semantics { heading() },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.otiumColors.accent),
                )
            }
        }
        book.description?.takeIf { it.isNotBlank() }?.let { Description(it) }
        Facts(book)
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by rememberSaveable { mutableStateOf(false) }
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 14.5.sp,
        lineHeight = (14.5 * DESCRIPTION_LINE_HEIGHT).sp,
        textAlign = TextAlign.Justify,
        hyphens = Hyphens.Auto,
    )
    Text(
        text = text,
        style = style,
        maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        modifier = Modifier.padding(top = 16.dp),
    )
    if (expanded || overflows) {
        OtiumButton(
            text = stringResource(
                if (expanded) R.string.detail_show_less else R.string.detail_show_more,
            ),
            onClick = { expanded = !expanded },
            style = OtiumButtonStyle.Ghost,
            compact = true,
        )
    }
}

/** A ruled table: label at the left, value at the right. */
@Composable
private fun Facts(book: Book) {
    val facts = listOfNotNull(
        book.categories.firstOrNull()?.let { R.string.detail_collection to it },
        book.publishedYear?.let { R.string.detail_published to it.toString() },
        book.publisher?.let { R.string.detail_publisher to it },
        book.totalPages.takeIf { it > 0 }?.let { R.string.detail_pages to it.toString() },
        book.language?.let { R.string.detail_language to it },
        book.isbn13?.let { R.string.detail_isbn to it },
    )
    val divider = MaterialTheme.otiumColors.divider
    val style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES)
    Column(modifier = Modifier.padding(top = 12.dp)) {
        facts.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineBottom(divider)
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(label),
                    style = style,
                    color = MaterialTheme.otiumColors.muted,
                )
                Text(
                    text = value,
                    style = style.copy(textAlign = TextAlign.End),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
