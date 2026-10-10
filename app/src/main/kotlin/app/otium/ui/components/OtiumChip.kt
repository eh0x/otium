package app.otium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

private val CORNER = 4.dp

/**
 * A choice in a single-select group (status, sort, shelf). Selected: accent border, a 14% accent
 * fill and a gold label; otherwise a hairline border and an ink label. The selection is also
 * exposed to screen readers, so it is not conveyed by color alone.
 */
@Composable
fun OtiumChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
) {
    val colors = MaterialTheme.otiumColors
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = MaterialTheme.shapes.small

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .focusRing(focused, colors.accent, CORNER)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) colors.accent else colors.divider, shape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = role,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimaryContainer else scheme.onBackground,
        )
    }
}

@PreviewLightDark
@Composable
private fun OtiumChipPreview() {
    var chosen by remember { mutableStateOf("Reading") }
    OtiumTheme {
        PaperBackground {
            FlowRow(
                modifier = Modifier.padding(22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All", "Unread", "Reading", "Read").forEach {
                    OtiumChip(label = it, selected = it == chosen, onClick = { chosen = it })
                }
            }
        }
    }
}
