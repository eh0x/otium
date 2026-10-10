package app.otium.ui.add.isbn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.domain.error.AppError
import app.otium.ui.add.AddDestination
import app.otium.ui.common.messageRes
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumIndeterminateRule
import app.otium.ui.components.OtiumScreenHeader
import app.otium.ui.components.otiumUnderlinedFieldColors
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors

/** Connects the ISBN entry screen to its ViewModel and hands each destination to [onDestination]. */
@Composable
fun IsbnEntryScreen(
    onBack: () -> Unit,
    onDestination: (AddDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IsbnEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDestination by rememberUpdatedState(onDestination)
    LaunchedEffect(state.destination) {
        state.destination?.let {
            currentOnDestination(it)
            viewModel.onNavigated()
        }
    }
    val actions = remember(viewModel, onBack) {
        IsbnEntryActions(
            onBack = onBack,
            onInputChange = viewModel::onInputChange,
            onLookUp = viewModel::onLookUp,
            onSaveForLater = viewModel::onSaveForLater,
            onEnterManually = viewModel::onEnterManually,
        )
    }
    IsbnEntryContent(state = state, actions = actions, modifier = modifier)
}

/** The ISBN entry screen as a pure function of [state]. */
@Composable
fun IsbnEntryContent(
    state: IsbnEntryUiState,
    actions: IsbnEntryActions,
    modifier: Modifier = Modifier,
) {
    PaperBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            OtiumScreenHeader(title = stringResource(R.string.isbn_title), onBack = actions.onBack)
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IsbnField(state, actions)
                if (state.isLooking) Consulting()
                state.lookupError?.let { LookupFailure(it, state.canSaveForLater, actions) }
                    ?: EnterManually(actions.onEnterManually)
            }
        }
    }
}

@Composable
private fun IsbnField(state: IsbnEntryUiState, actions: IsbnEntryActions) {
    TextField(
        value = state.input,
        onValueChange = actions.onInputChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                stringResource(R.string.isbn_field).uppercase(),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        textStyle = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
        colors = otiumUnderlinedFieldColors(),
        supportingText = {
            Text(stringResource(state.inputError?.messageRes() ?: R.string.isbn_hint))
        },
        isError = state.inputError != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { actions.onLookUp() }),
    )
    OtiumButton(
        text = stringResource(R.string.isbn_look_up),
        onClick = actions.onLookUp,
        enabled = state.canLookUp,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Why the lookup failed, in plain words, and what can be done instead. */
@Composable
private fun LookupFailure(error: AppError, canSaveForLater: Boolean, actions: IsbnEntryActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        OtiumButton(
            text = stringResource(R.string.isbn_try_again),
            onClick = actions.onLookUp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (canSaveForLater) {
            OtiumButton(
                text = stringResource(R.string.isbn_save_for_later),
                onClick = actions.onSaveForLater,
                style = OtiumButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.isbn_save_for_later_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.otiumColors.muted,
            )
        }
        EnterManually(actions.onEnterManually)
    }
}

/** The lookup under way: the indeterminate rule over "Consulting the catalogue…". */
@Composable
private fun Consulting() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OtiumIndeterminateRule()
        Text(
            text = stringResource(R.string.add_consulting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.otiumColors.muted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun EnterManually(onClick: () -> Unit) {
    OtiumButton(
        text = stringResource(R.string.add_enter_manually),
        onClick = onClick,
        style = OtiumButtonStyle.Ghost,
        compact = true,
    )
}
