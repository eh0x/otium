package app.otium.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.domain.model.Book
import app.otium.ui.common.messageRes
import app.otium.ui.common.rememberCoverPainter
import app.otium.ui.components.BookCover
import app.otium.ui.components.OtiumTopBar
import app.otium.ui.components.StatusChip
import app.otium.ui.theme.PaperBackground

private val COVER_WIDTH = 160.dp
private const val DESCRIPTION_COLLAPSED_LINES = 4
private const val DESCRIPTION_TOGGLE_THRESHOLD = 200

/** Connects the detail screen to its ViewModel and leaves the screen once the book is deleted. */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val actions = remember(viewModel, onBack) {
        DetailActions(
            onBack = onBack,
            onProgressConfirmed = viewModel::onProgressConfirmed,
            onMarkFinished = viewModel::onMarkFinished,
            onTotalPagesConfirmed = viewModel::onTotalPagesConfirmed,
            onDelete = viewModel::onDelete,
            onErrorShown = viewModel::onErrorShown,
        )
    }
    DetailContent(state = state, actions = actions, modifier = modifier)
}

/** The detail screen as a pure function of [state], so it can be tested without a ViewModel. */
@Composable
fun DetailContent(state: DetailUiState, actions: DetailActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = state.error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            actions.onErrorShown()
        }
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val book = state.book

    PaperBackground(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                OtiumTopBar(
                    title = "",
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.detail_back),
                            )
                        }
                    },
                    actions = {
                        if (book != null) OverflowMenu(onDelete = { confirmDelete = true })
                    },
                )
            },
        ) { padding ->
            when {
                book != null -> BookDetails(book, actions, Modifier.padding(padding))

                !state.isLoading -> Text(
                    text = stringResource(R.string.detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(padding)
                        .padding(32.dp),
                )
            }
        }
        if (confirmDelete && book != null) {
            DeleteDialog(
                title = book.title,
                onConfirm = {
                    confirmDelete = false
                    actions.onDelete()
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun OverflowMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.detail_more_options),
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.detail_delete)) },
            onClick = {
                open = false
                onDelete()
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookDetails(book: Book, actions: DetailActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BookCover(
            title = book.title,
            painter = rememberCoverPainter(book.coverPath),
            modifier = Modifier.width(COVER_WIDTH),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            book.subtitle?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) }
            Text(
                text = book.authors.joinToString(", "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusChip(status = book.status)
        ProgressCard(book = book, actions = actions)
        val labels = book.categories + book.tags
        if (labels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        book.description?.takeIf { it.isNotBlank() }?.let { Description(it) }
        Facts(book)
    }
}

@Composable
private fun DeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_delete_title)) },
        text = { Text(stringResource(R.string.detail_delete_message, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsible = text.length > DESCRIPTION_TOGGLE_THRESHOLD
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.detail_description),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded || !collapsible) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsible) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) R.string.detail_show_less else R.string.detail_show_more,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Facts(book: Book) {
    val facts = listOfNotNull(
        book.publisher?.let { R.string.detail_publisher to it },
        book.publishedYear?.let { R.string.detail_published to it.toString() },
        book.language?.let { R.string.detail_language to it },
        book.isbn13?.let { R.string.detail_isbn to it },
    )
    if (facts.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        facts.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
