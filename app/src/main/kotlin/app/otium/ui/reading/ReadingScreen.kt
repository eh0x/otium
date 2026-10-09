package app.otium.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.ui.common.messageRes
import app.otium.ui.common.rememberCoverPainter
import app.otium.ui.components.BookCover
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumProgressBar
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors

private val COVER_WIDTH = 76.dp
private val FINISHED_DOT = 11.dp

/** Connects the Reading tab to its ViewModel. */
@Composable
fun ReadingScreen(
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReadingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onOpenBook) {
        ReadingActions(
            onOpenBook = onOpenBook,
            onAddPages = viewModel::onAddPages,
            onErrorShown = viewModel::onErrorShown,
        )
    }
    ReadingContent(state = state, actions = actions, modifier = modifier)
}

/** The Reading tab as a pure function of [state]: open books first, then the recently finished. */
@Composable
fun ReadingContent(state: ReadingUiState, actions: ReadingActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = state.error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            actions.onErrorShown()
        }
    }
    PaperBackground(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 18.dp),
        ) {
            item(key = "title") { Title() }
            if (!state.isLoading && state.open.isEmpty()) {
                item(key = "empty") { EmptyDesk() }
            }
            items(items = state.open, key = { it.id }) { book -> OpenBook(book, actions) }
            if (state.finished.isNotEmpty()) {
                item(key = "finished") { FinishedHeading() }
                items(items = state.finished, key = { "finished:${it.id}" }) { book ->
                    FinishedBook(book = book, onOpen = { actions.onOpenBook(book.id) })
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Title() {
    Column(modifier = Modifier.padding(bottom = 18.dp)) {
        Text(
            text = stringResource(R.string.reading_kicker).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.reading_title),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun EmptyDesk() {
    Text(
        text = stringResource(R.string.reading_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.otiumColors.muted,
        modifier = Modifier
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .padding(vertical = 16.dp),
    )
}

/** An open book: cover, title, author, the page in figures, a rule and the two actions. */
@Composable
private fun OpenBook(book: BookSummary, actions: ReadingActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BookCover(
            title = book.title,
            authors = book.authors,
            painter = rememberCoverPainter(book.coverPath),
            modifier = Modifier
                .width(COVER_WIDTH)
                .clickable(role = Role.Button) { actions.onOpenBook(book.id) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.authors,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.otiumColors.muted,
            )
            PageLine(book)
            OtiumProgressBar(
                progress = book.currentPage.toFloat() / book.totalPages,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OtiumButton(
                    text = stringResource(R.string.reading_add_pages),
                    onClick = { actions.onAddPages(book) },
                    style = OtiumButtonStyle.Secondary,
                    compact = true,
                    contentDescription = stringResource(
                        R.string.reading_add_pages_description,
                        book.title,
                    ),
                )
                OtiumButton(
                    text = stringResource(R.string.reading_open),
                    onClick = { actions.onOpenBook(book.id) },
                    style = OtiumButtonStyle.Ghost,
                    compact = true,
                    contentDescription = stringResource(
                        R.string.reading_open_description,
                        book.title,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PageLine(book: BookSummary) {
    Row(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = book.currentPage.toString(),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFeatureSettings = TABULAR_FIGURES,
            ),
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = pluralStringResource(
                R.plurals.progress_of_pages,
                book.totalPages,
                book.totalPages,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier
                .padding(start = 6.dp)
                .alignByBaseline(),
        )
    }
}

@Composable
private fun FinishedHeading() {
    Text(
        text = stringResource(R.string.reading_finished),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(top = 22.dp)
            .fillMaxWidth()
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun FinishedBook(book: BookSummary, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(FINISHED_DOT)
                .background(MaterialTheme.otiumColors.accent, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.authors,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.otiumColors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ReadingContentPreview() {
    val open = BookSummary(
        id = 1,
        title = "Meditations",
        authors = "Marcus Aurelius",
        coverPath = null,
        coverSourceUrl = null,
        currentPage = 212,
        totalPages = 304,
        status = BookStatus.IN_PROGRESS,
    )
    val finished = open.copy(
        id = 2,
        title = "Ethics",
        authors = "Baruch Spinoza",
        currentPage = 304,
    )
    OtiumTheme {
        ReadingContent(
            state = ReadingUiState(
                isLoading = false,
                open = listOf(open),
                finished = listOf(finished),
            ),
            actions = ReadingActions(),
        )
    }
}
