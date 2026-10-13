package app.otium.ui.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.otium.domain.model.SortOrder
import app.otium.ui.components.OtiumMasthead
import app.otium.ui.theme.PaperBackground

/** Connects the library screen to its ViewModel. Holds no layout of its own. */
@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenAddSheet: () -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onOpenBook, onOpenAddSheet, onOpenCatalog) {
        LibraryActions(
            onQueryChange = viewModel::onQueryChange,
            onStatusSelected = viewModel::onStatusSelected,
            onSortSelected = viewModel::onSortSelected,
            onClearFilters = viewModel::onClearFilters,
            onViewModeSelected = viewModel::onViewModeSelected,
            onOpenBook = onOpenBook,
            onOpenAddSheet = onOpenAddSheet,
            onSeedDebugData = viewModel::onSeedDebugData,
            onOpenCatalog = onOpenCatalog,
        )
    }
    LibraryContent(state = state, actions = actions, modifier = modifier)
}

/**
 * The library screen as a pure function of [state], so it can be tested without a ViewModel:
 * the masthead, search, status tabs and a meta line above the shelves or the index.
 */
@Composable
fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    var arrangeOpen by rememberSaveable { mutableStateOf(false) }
    PaperBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OtiumMasthead(modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp))
                // The menu holds developer tools only, so it exists only in builds that have them.
                if (state.debugToolsAvailable) {
                    MoreMenu(actions = actions, modifier = Modifier.align(Alignment.TopEnd))
                }
            }
            SearchRow(state = state, actions = actions, onOpenArrange = { arrangeOpen = true })
            StatusTabs(selected = state.status, onSelected = actions.onStatusSelected)
            MetaLine(state = state, onOpenArrange = { arrangeOpen = true })
            LibraryBody(state = state, actions = actions)
        }
        if (arrangeOpen) {
            ArrangeSheet(state = state, actions = actions, onDismiss = { arrangeOpen = false })
        }
    }
}

@StringRes
internal fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.TITLE -> R.string.sort_title
    SortOrder.AUTHOR -> R.string.sort_author
    SortOrder.SERIES -> R.string.sort_series
    SortOrder.RECENTLY_ADDED -> R.string.sort_by_recently_added
    SortOrder.LAST_UPDATED -> R.string.sort_last_updated
    SortOrder.PROGRESS -> R.string.sort_progress
}

/** "By title", for the meta line. */
@StringRes
internal fun SortOrder.byLabelRes(): Int = when (this) {
    SortOrder.TITLE -> R.string.sort_by_title
    SortOrder.AUTHOR -> R.string.sort_by_author
    SortOrder.SERIES -> R.string.sort_by_series
    SortOrder.RECENTLY_ADDED -> R.string.sort_by_recently_added
    SortOrder.LAST_UPDATED -> R.string.sort_by_last_updated
    SortOrder.PROGRESS -> R.string.sort_by_progress
}

/** The orders offered in the arrange sheet. Recently added stays in the model but is not offered. */
internal val ArrangeSortOrders =
    listOf(SortOrder.TITLE, SortOrder.AUTHOR, SortOrder.LAST_UPDATED, SortOrder.PROGRESS)

@Composable
private fun MoreMenu(actions: LibraryActions, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_ellipsis_vertical),
                contentDescription = stringResource(R.string.library_more_options),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_debug_seed)) },
                onClick = {
                    open = false
                    actions.onSeedDebugData()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_debug_catalog)) },
                onClick = {
                    open = false
                    actions.onOpenCatalog()
                },
            )
        }
    }
}
