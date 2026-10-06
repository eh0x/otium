package app.otium.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otium.R
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortOrder
import app.otium.ui.add.AddMethod
import app.otium.ui.add.AddSheet
import app.otium.ui.components.OtiumTopBar
import app.otium.ui.components.labelRes
import app.otium.ui.theme.PaperBackground

/** Connects the library screen to its ViewModel. Holds no layout of its own. */
@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onAddBook: (AddMethod) -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onOpenBook, onAddBook, onOpenCatalog) {
        LibraryActions(
            onQueryChange = viewModel::onQueryChange,
            onStatusSelected = viewModel::onStatusSelected,
            onCategoryToggled = viewModel::onCategoryToggled,
            onTagToggled = viewModel::onTagToggled,
            onSortSelected = viewModel::onSortSelected,
            onClearFilters = viewModel::onClearFilters,
            onViewModeSelected = viewModel::onViewModeSelected,
            onOpenBook = onOpenBook,
            onAddBook = onAddBook,
            onSeedDebugData = viewModel::onSeedDebugData,
            onOpenCatalog = onOpenCatalog,
        )
    }
    LibraryContent(state = state, actions = actions, modifier = modifier)
}

/** The library screen as a pure function of [state], so it can be tested without a ViewModel. */
@Composable
fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    PaperBackground(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { LibraryTopBar(state = state, actions = actions) },
            floatingActionButton = {
                FloatingActionButton(onClick = { addOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.library_add_book),
                    )
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                SearchField(
                    query = state.query,
                    onQueryChange = actions.onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                StatusFilterRow(selected = state.status, onSelected = actions.onStatusSelected)
                ControlsRow(
                    state = state,
                    actions = actions,
                    onOpenFilters = { filtersOpen = true },
                )
                LibraryBody(
                    state = state,
                    actions = actions,
                    onOpenAddSheet = { addOpen = true },
                )
            }
        }
        if (filtersOpen) {
            FilterSheet(state = state, actions = actions, onDismiss = { filtersOpen = false })
        }
        if (addOpen) {
            AddSheet(
                onChoose = { method ->
                    addOpen = false
                    actions.onAddBook(method)
                },
                onDismiss = { addOpen = false },
            )
        }
    }
}

@Composable
private fun LibraryTopBar(state: LibraryUiState, actions: LibraryActions) {
    OtiumTopBar(
        title = stringResource(R.string.library_title),
        actions = {
            ViewModeAction(selected = state.viewMode, onSelected = actions.onViewModeSelected)
            if (state.debugToolsAvailable) {
                DebugMenu(onSeed = actions.onSeedDebugData, onOpenCatalog = actions.onOpenCatalog)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.library_search_clear),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun StatusFilterRow(selected: BookStatus?, onSelected: (BookStatus?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(R.string.filter_status_all)) },
            )
        }
        items(items = BookStatus.entries, key = { it.name }) { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelected(status) },
                label = { Text(stringResource(status.labelRes())) },
            )
        }
    }
}

private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.TITLE -> R.string.sort_title
    SortOrder.AUTHOR -> R.string.sort_author
    SortOrder.RECENTLY_ADDED -> R.string.sort_recently_added
    SortOrder.LAST_UPDATED -> R.string.sort_last_updated
    SortOrder.PROGRESS -> R.string.sort_progress
}

@Composable
private fun ControlsRow(
    state: LibraryUiState,
    actions: LibraryActions,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.library_book_count,
                state.books.size,
                state.books.size,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        SortMenu(selected = state.sort, onSelected = actions.onSortSelected)
        TextButton(onClick = onOpenFilters) {
            Text(
                if (state.selectedLabelCount > 0) {
                    stringResource(R.string.library_filters_active, state.selectedLabelCount)
                } else {
                    stringResource(R.string.library_filters)
                },
            )
        }
    }
}

@Composable
private fun SortMenu(selected: SortOrder, onSelected: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.library_sort)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        SortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(order.labelRes()),
                        style = if (order == selected) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                    )
                },
                onClick = {
                    open = false
                    onSelected(order)
                },
            )
        }
    }
}

/** One button that switches to the other layout, labelled with where it will go. */
@Composable
private fun ViewModeAction(selected: LibraryViewMode, onSelected: (LibraryViewMode) -> Unit) {
    val (target, label) = when (selected) {
        LibraryViewMode.SHELF -> LibraryViewMode.LIST to R.string.library_view_list
        LibraryViewMode.LIST -> LibraryViewMode.SHELF to R.string.library_view_shelf
    }
    TextButton(onClick = { onSelected(target) }) { Text(stringResource(label)) }
}

@Composable
private fun DebugMenu(onSeed: () -> Unit, onOpenCatalog: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.library_more_options),
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_debug_seed)) },
            onClick = {
                open = false
                onSeed()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_debug_catalog)) },
            onClick = {
                open = false
                onOpenCatalog()
            },
        )
    }
}
