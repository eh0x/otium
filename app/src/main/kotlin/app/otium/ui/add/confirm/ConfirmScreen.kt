package app.otium.ui.add.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.ui.common.messageRes
import app.otium.ui.components.OtiumTopBar
import app.otium.ui.theme.PaperBackground
import kotlinx.coroutines.launch

/**
 * Connects the confirm/edit screen to its ViewModel. Once the book is saved it hands the id to
 * [onSaved]; [onOpenBook] opens a book that already has the ISBN.
 */
@Composable
fun ConfirmScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfirmViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.savedBookId) { state.savedBookId?.let(currentOnSaved) }
    val actions = remember(viewModel, onBack, onOpenBook) {
        ConfirmActions(
            onBack = onBack,
            onFormChange = viewModel::onFormChange,
            onCoverSelected = viewModel::onCoverSelected,
            onSave = viewModel::onSave,
            onOpenBook = onOpenBook,
            onDuplicateDismissed = viewModel::onDuplicateDismissed,
            onErrorShown = viewModel::onErrorShown,
        )
    }
    ConfirmContent(state = state, actions = actions, modifier = modifier)
}

/** The confirm/edit screen as a pure function of [state], so it can be tested without a ViewModel. */
@Composable
fun ConfirmContent(state: ConfirmUiState, actions: ConfirmActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val errorMessage = state.error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            actions.onErrorShown()
        }
    }
    val cameraDenied = stringResource(R.string.confirm_camera_denied)

    PaperBackground(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { ConfirmTopBar(state, actions) },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (state.isSaving || state.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
                }
                when {
                    state.notFound -> Text(
                        text = stringResource(R.string.confirm_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp),
                    )

                    !state.isLoading -> ConfirmBody(
                        state = state,
                        actions = actions,
                        onCameraDenied = { scope.launch { snackbar.showSnackbar(cameraDenied) } },
                    )
                }
            }
        }
        state.duplicateOf?.let { existingId ->
            DuplicateDialog(
                onOpen = { actions.onOpenBook(existingId) },
                onDismiss = actions.onDuplicateDismissed,
            )
        }
    }
}

@Composable
private fun ConfirmBody(
    state: ConfirmUiState,
    actions: ConfirmActions,
    onCameraDenied: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverSection(
            title = state.form.title,
            state = state,
            onCoverSelected = actions.onCoverSelected,
            onCameraDenied = onCameraDenied,
        )
        ConfirmForm(
            form = state.form,
            pagesUnknown = state.pagesUnknown,
            knownTags = state.knownTags,
            onFormChange = actions.onFormChange,
        )
    }
}

@Composable
private fun ConfirmTopBar(state: ConfirmUiState, actions: ConfirmActions) {
    OtiumTopBar(
        title = stringResource(
            if (state.isEditing) R.string.confirm_title_edit else R.string.confirm_title_new,
        ),
        navigationIcon = {
            IconButton(onClick = actions.onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.detail_back),
                )
            }
        },
        actions = {
            TextButton(onClick = actions.onSave, enabled = state.canSave) {
                Text(stringResource(R.string.action_save))
            }
        },
    )
}

@Composable
private fun DuplicateDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_duplicate_title)) },
        text = { Text(stringResource(R.string.confirm_duplicate_message)) },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.confirm_duplicate_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
