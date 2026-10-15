package app.otium.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.domain.model.Book
import app.otium.domain.model.Note
import app.otium.ui.common.messageRes
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumSnackbarHost
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

/** Connects the detail screen to its ViewModel and leaves the screen once the book is deleted. */
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val actions = remember(viewModel, onBack, onEdit) {
        DetailActions(
            onBack = onBack,
            onEdit = onEdit,
            onProgressConfirmed = viewModel::onProgressConfirmed,
            onMarkFinished = viewModel::onMarkFinished,
            onTotalPagesConfirmed = viewModel::onTotalPagesConfirmed,
            onDelete = viewModel::onDelete,
            onNoteSaved = viewModel::onNoteSaved,
            onNoteDeleted = viewModel::onNoteDeleted,
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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            TopRow(hasBook = book != null, actions = actions, onDelete = { confirmDelete = true })
            when {
                book != null -> BookDetails(book, state.notes, actions)

                !state.isLoading -> Text(
                    text = stringResource(R.string.detail_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 32.dp),
                )
            }
        }
        OtiumSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
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

/** Back, and for a book that exists, the menu to edit or delete it. */
@Composable
private fun TopRow(hasBook: Boolean, actions: DetailActions, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_arrow_left),
                contentDescription = stringResource(R.string.detail_back),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (hasBook) OverflowMenu(onEdit = actions.onEdit, onDelete = onDelete)
    }
}

@Composable
private fun OverflowMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.otiumColors
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_ellipsis_vertical),
                contentDescription = stringResource(R.string.detail_more_options),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = MaterialTheme.shapes.small,
            containerColor = colors.sheet,
            border = BorderStroke(1.dp, colors.divider),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_edit)) },
                onClick = {
                    open = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_delete)) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun BookDetails(book: Book, notes: List<Note>, actions: DetailActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp),
    ) {
        DetailHeader(book)
        ProgressSection(
            book = book,
            actions = actions,
            modifier = Modifier
                .padding(top = 26.dp)
                .hairlineTop(MaterialTheme.otiumColors.divider)
                .padding(top = 16.dp),
        )
        NotesSection(
            notes = notes,
            actions = actions,
            modifier = Modifier
                .padding(top = 26.dp)
                .hairlineTop(MaterialTheme.otiumColors.divider)
                .padding(top = 16.dp),
        )
        AboutTab(book = book, modifier = Modifier.padding(top = 26.dp))
    }
}

@Composable
private fun DeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    OtiumDialog(
        title = stringResource(R.string.detail_delete_title),
        body = stringResource(R.string.detail_delete_message, title),
        onDismissRequest = onDismiss,
        actions = {
            OtiumButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(text = stringResource(R.string.action_delete), onClick = onConfirm)
        },
    )
}
