package app.otium.ui.add.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.ui.common.messageRes
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumIndeterminateRule
import app.otium.ui.components.OtiumSnackbarHost
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            ConfirmHeader(state, actions)
            if (state.isSaving || state.isLoading) {
                OtiumIndeterminateRule(modifier = Modifier.padding(horizontal = 22.dp))
            }
            when {
                state.notFound -> Text(
                    text = stringResource(R.string.confirm_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 32.dp),
                )

                !state.isLoading -> ConfirmBody(
                    state = state,
                    actions = actions,
                    onCameraDenied = { scope.launch { snackbar.showSnackbar(cameraDenied) } },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!state.notFound) ConfirmFooter(state, actions)
        }
        OtiumSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CoverSection(
            hint = stringResource(
                if (state.isEditing) R.string.confirm_hint_edit else R.string.confirm_hint_new,
            ),
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

/** Close, and what the screen is for: checking a new book or editing one. */
@Composable
private fun ConfirmHeader(state: ConfirmUiState, actions: ConfirmActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_x),
                contentDescription = stringResource(R.string.confirm_close),
            )
        }
        Text(
            text = stringResource(
                if (state.isEditing) R.string.confirm_title_edit else R.string.confirm_title_new,
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Cancel, and the save that names what it does. Save waits for a title. */
@Composable
private fun ConfirmFooter(state: ConfirmUiState, actions: ConfirmActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .padding(start = 22.dp, top = 12.dp, end = 22.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OtiumButton(
            text = stringResource(R.string.action_cancel),
            onClick = actions.onBack,
            style = OtiumButtonStyle.Secondary,
            modifier = Modifier.weight(1f),
        )
        OtiumButton(
            text = stringResource(
                if (state.isEditing) R.string.confirm_save_edit else R.string.confirm_save_new,
            ),
            onClick = actions.onSave,
            enabled = state.canSave,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun DuplicateDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    OtiumDialog(
        title = stringResource(R.string.confirm_duplicate_title),
        body = stringResource(R.string.confirm_duplicate_message),
        onDismissRequest = onDismiss,
        actions = {
            OtiumButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                style = OtiumButtonStyle.Secondary,
            )
            OtiumButton(text = stringResource(R.string.confirm_duplicate_open), onClick = onOpen)
        },
    )
}
