package app.otium.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryViewMode
import app.otium.ui.components.hairlineBottom
import app.otium.ui.components.labelRes
import app.otium.ui.theme.TABULAR_FIGURES
import app.otium.ui.theme.otiumColors

/** The status tabs, in catalogue order. Null is "All". */
internal val STATUS_TABS =
    listOf(null, BookStatus.NOT_STARTED, BookStatus.IN_PROGRESS, BookStatus.READ)

/**
 * Search the catalogue, switch layout, and open the arrange sheet. The row's hairline is the
 * field's underline: no box, no border.
 */
@Composable
internal fun SearchRow(state: LibraryUiState, actions: LibraryActions, onOpenArrange: () -> Unit) {
    val colors = MaterialTheme.otiumColors
    Row(
        modifier = Modifier
            .padding(start = 22.dp, top = 14.dp, end = 22.dp)
            .fillMaxWidth()
            .hairlineBottom(colors.divider),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lucide_search),
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(18.dp),
        )
        SearchField(
            query = state.query,
            onQueryChange = actions.onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        if (state.query.isNotEmpty()) {
            HeaderIcon(
                icon = R.drawable.ic_lucide_x,
                description = stringResource(R.string.library_search_clear),
                tint = colors.muted,
                onClick = { actions.onQueryChange("") },
            )
        }
        val (target, icon) = when (state.viewMode) {
            LibraryViewMode.SHELF -> LibraryViewMode.LIST to R.drawable.ic_lucide_list
            LibraryViewMode.LIST -> LibraryViewMode.SHELF to R.drawable.ic_lucide_layout_grid
        }
        HeaderIcon(
            icon = icon,
            description = stringResource(R.string.library_change_layout),
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = { actions.onViewModeSelected(target) },
        )
        HeaderIcon(
            icon = R.drawable.ic_lucide_list_filter,
            description = stringResource(R.string.library_arrange),
            tint = if (state.isArranged) colors.accent else MaterialTheme.colorScheme.onBackground,
            onClick = onOpenArrange,
        )
    }
}

/** The search field itself: italic, with no box; the row's hairline underlines it. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.otiumColors
    val keyboard = LocalSoftwareKeyboardController.current
    val searchLabel = stringResource(R.string.library_search_label)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onBackground,
    )
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.semantics { contentDescription = searchLabel },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { field ->
            Box(modifier = Modifier.padding(vertical = 11.dp)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_search_hint),
                        style = textStyle.copy(color = colors.muted),
                    )
                }
                field()
            }
        },
    )
}

@Composable
private fun HeaderIcon(icon: Int, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** All · Unread · Reading · Read. The active tab is ink with an accent underline. */
@Composable
internal fun StatusTabs(selected: BookStatus?, onSelected: (BookStatus?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 4.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
        STATUS_TABS.forEach { status ->
            val active = status == selected
            Column(
                // As wide as the label, so the underline matches it.
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .heightIn(min = 44.dp)
                    .selectable(selected = active, role = Role.Tab, onClick = {
                        onSelected(status)
                    }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(status?.labelRes() ?: R.string.filter_status_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.otiumColors.muted
                    },
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .height(2.dp)
                        .fillMaxWidth()
                        .background(
                            if (active) MaterialTheme.otiumColors.accent else Color.Transparent,
                        ),
                )
            }
        }
    }
}

/** "12 volumes" (with the status when filtered) and, on the right, the sort that opens Arrange. */
@Composable
internal fun MetaLine(state: LibraryUiState, onOpenArrange: () -> Unit) {
    val muted = MaterialTheme.otiumColors.muted
    val style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES)
    val count =
        pluralStringResource(R.plurals.library_book_count, state.books.size, state.books.size)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, top = 8.dp, end = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.status?.let {
                stringResource(R.string.dot_joined, count, stringResource(it.labelRes()))
            } ?: count,
            style = style,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onOpenArrange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = stringResource(state.sort.byLabelRes()), style = style, color = muted)
            Icon(
                painter = painterResource(R.drawable.ic_lucide_chevron_down),
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
