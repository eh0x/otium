package app.otium.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.otium.R
import app.otium.ui.components.OtiumButton
import app.otium.ui.components.OtiumChip
import app.otium.ui.components.OtiumSheet
import app.otium.ui.components.labelRes
import app.otium.ui.theme.otiumColors

/** Status and sort for the library. Choices apply at once; Done only closes the sheet. */
@Composable
internal fun ArrangeSheet(state: LibraryUiState, actions: LibraryActions, onDismiss: () -> Unit) {
    OtiumSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.arrange_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 32.sp,
                letterSpacing = (-0.02).em,
            ),
            modifier = Modifier.semantics { heading() },
        )
        SectionLabel(stringResource(R.string.arrange_status))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            STATUS_TABS.forEach { status ->
                OtiumChip(
                    label = stringResource(status?.labelRes() ?: R.string.filter_status_all),
                    selected = state.status == status,
                    onClick = { actions.onStatusSelected(status) },
                )
            }
        }
        SectionLabel(stringResource(R.string.arrange_sort))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArrangeSortOrders.forEach { order ->
                OtiumChip(
                    label = stringResource(order.labelRes()),
                    selected = state.sort == order,
                    onClick = { actions.onSortSelected(order) },
                )
            }
        }
        OtiumButton(
            text = stringResource(R.string.filter_done),
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)
                .heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.otiumColors.muted,
        modifier = Modifier
            .padding(top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}
