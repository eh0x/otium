package app.otium.ui.add.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.domain.model.BookCandidate
import app.otium.ui.add.AddDestination
import app.otium.ui.common.messageRes
import app.otium.ui.components.BookCover
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumButtonStyle
import app.otium.ui.components.OtiumIndeterminateRule
import app.otium.ui.components.OtiumScreenHeader
import app.otium.ui.components.OtiumSnackbarHost
import app.otium.ui.components.hairlineBottom
import app.otium.ui.components.otiumUnderlinedFieldColors
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors
import coil3.compose.rememberAsyncImagePainter

private val THUMBNAIL_WIDTH = 48.dp

/** Connects the title search screen to its ViewModel and hands each destination to [onDestination]. */
@Composable
fun TitleSearchScreen(
    onBack: () -> Unit,
    onDestination: (AddDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TitleSearchViewModel = hiltViewModel(),
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
        TitleSearchActions(
            onBack = onBack,
            onQueryChange = viewModel::onQueryChange,
            onSearch = viewModel::onSearch,
            onResultChosen = viewModel::onResultChosen,
            onEnterManually = viewModel::onEnterManually,
            onErrorShown = viewModel::onErrorShown,
        )
    }
    TitleSearchContent(state = state, actions = actions, modifier = modifier)
}

/** The title search screen as a pure function of [state]. */
@Composable
fun TitleSearchContent(
    state: TitleSearchUiState,
    actions: TitleSearchActions,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = state.error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbar.showSnackbar(errorMessage)
            actions.onErrorShown()
        }
    }
    PaperBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            OtiumScreenHeader(
                title = stringResource(R.string.search_title),
                onBack = actions.onBack,
            )
            QueryField(state, actions)
            if (state.isSearching) {
                OtiumIndeterminateRule(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
            Results(state, actions)
        }
        OtiumSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun QueryField(state: TitleSearchUiState, actions: TitleSearchActions) {
    TextField(
        value = state.query,
        onValueChange = actions.onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        label = {
            Text(
                stringResource(R.string.search_field).uppercase(),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
        colors = otiumUnderlinedFieldColors(),
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = actions.onSearch, enabled = state.canSearch) {
                Icon(
                    painter = painterResource(R.drawable.ic_lucide_search),
                    contentDescription = stringResource(R.string.search_action),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
    )
}

@Composable
private fun Results(state: TitleSearchUiState, actions: TitleSearchActions) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.noResults) {
            item(key = "none") {
                Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        itemsIndexed(state.results, key = { index, _ -> index }) { _, candidate ->
            ResultRow(candidate, onClick = { actions.onResultChosen(candidate) })
        }
        item(key = "manual") {
            OtiumButton(
                text = stringResource(R.string.add_enter_manually),
                onClick = actions.onEnterManually,
                style = OtiumButtonStyle.Ghost,
                compact = true,
            )
        }
    }
}

/** One edition: thumbnail, title, authors and when and by whom it was published. */
@Composable
private fun ResultRow(candidate: BookCandidate, onClick: () -> Unit) {
    val authors = candidate.authors.joinToString(", ")
    val description = stringResource(R.string.search_result_description, candidate.title, authors)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .hairlineBottom(MaterialTheme.otiumColors.divider)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            title = candidate.title,
            authors = authors,
            painter = candidate.coverUrl?.let { rememberAsyncImagePainter(it) },
            modifier = Modifier.width(THUMBNAIL_WIDTH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (authors.isNotEmpty()) {
                Text(
                    text = authors,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.otiumColors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val edition = listOfNotNull(candidate.publishedYear?.toString(), candidate.publisher)
            if (edition.isNotEmpty()) {
                Text(
                    text = edition.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.otiumColors.muted,
                )
            }
        }
    }
}
