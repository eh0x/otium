package app.otium.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.ui.components.hairlineTop
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.otiumColors

private val BAR_HEIGHT = 74.dp
private val ADD_SIZE = 54.dp
private val ADD_LIFT = 18.dp

/**
 * The bottom bar: Library, Reading, a raised Add action, then Settings. The selected tab shows an
 * accent icon and a gold label; tabs are exposed as selectable tabs, so the choice is not
 * conveyed by color alone.
 */
@Composable
fun OtiumBottomBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .hairlineTop(MaterialTheme.otiumColors.divider)
            .navigationBarsPadding()
            .heightIn(min = BAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab(TopLevelDestination.LIBRARY, selected, onSelect)
        Tab(TopLevelDestination.READING, selected, onSelect)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AddAction(onAdd)
        }
        Tab(TopLevelDestination.SETTINGS, selected, onSelect)
    }
}

@Composable
private fun RowScope.Tab(
    destination: TopLevelDestination,
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val isSelected = destination == selected
    val colors = MaterialTheme.otiumColors
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = { onSelect(destination) },
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(destination.icon),
            contentDescription = null,
            tint = if (isSelected) colors.accent else colors.muted,
            modifier = Modifier.size(23.dp),
        )
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddAction(onAdd: () -> Unit) {
    val colors = MaterialTheme.otiumColors
    val description = stringResource(R.string.add_book_title)
    Box(
        modifier = Modifier
            .offset(y = -ADD_LIFT)
            .size(ADD_SIZE)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                ambientColor = colors.cover.shadow,
                spotColor = colors.cover.shadow,
            )
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .border(1.dp, colors.accent, CircleShape)
            .clickable(role = Role.Button, onClick = onAdd)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lucide_plus),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(24.dp),
        )
    }
}

@PreviewLightDark
@Composable
private fun OtiumBottomBarPreview() {
    OtiumTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(top = 24.dp)) {
            OtiumBottomBar(selected = TopLevelDestination.LIBRARY, onSelect = {}, onAdd = {})
        }
    }
}
