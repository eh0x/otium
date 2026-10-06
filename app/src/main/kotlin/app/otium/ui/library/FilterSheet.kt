package app.otium.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.otium.R

/** Category and tag filters. Choices apply immediately; the sheet only collects them. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    state: LibraryUiState,
    actions: LibraryActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val noLabels = state.availableCategories.isEmpty() && state.availableTags.isEmpty()
            if (noLabels) {
                Text(
                    text = stringResource(R.string.filter_none_available),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (state.availableCategories.isNotEmpty()) {
                SheetSection(title = stringResource(R.string.filter_categories)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableCategories.forEach { name ->
                            FilterChip(
                                selected = name in state.categories,
                                onClick = { actions.onCategoryToggled(name) },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
            if (state.availableTags.isNotEmpty()) {
                SheetSection(title = stringResource(R.string.filter_tags)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableTags.forEach { name ->
                            FilterChip(
                                selected = name in state.tags,
                                onClick = { actions.onTagToggled(name) },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = actions.onClearFilters, enabled = state.hasActiveFilters) {
                    Text(stringResource(R.string.filter_clear))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.filter_done)) }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}
