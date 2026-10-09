package app.otium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

private const val DISABLED_ALPHA = 0.45f
private const val INK_HOVER = 0.07f
private const val INK_PRESSED = 0.14f
private const val ACCENT_HOVER = 0.10f
private const val ACCENT_PRESSED = 0.18f
private val CORNER = 4.dp

/**
 * An outlined Otium button. [compact] lowers the minimum height from 46 dp to 40 dp, for actions
 * inside a row. The label wraps rather than clips at large font scales.
 */
@Composable
fun OtiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OtiumButtonStyle = OtiumButtonStyle.Primary,
    enabled: Boolean = true,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.otiumColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()

    val (label, border, tint) = buttonColors(style, enabled, pressed, hovered)
    val shape = MaterialTheme.shapes.small

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) 40.dp else 46.dp)
            .focusRing(focused, colors.accent, CORNER)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(tint)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = label, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = label,
            textAlign = TextAlign.Center,
        )
    }
}

private data class ButtonColors(val label: Color, val border: Color, val tint: Color)

@Composable
private fun buttonColors(
    style: OtiumButtonStyle,
    enabled: Boolean,
    pressed: Boolean,
    hovered: Boolean,
): ButtonColors {
    val accent = MaterialTheme.otiumColors.accent
    val ink = MaterialTheme.colorScheme.onBackground
    val inkTinted = style == OtiumButtonStyle.Secondary
    val border = when (style) {
        OtiumButtonStyle.Primary -> accent
        OtiumButtonStyle.Secondary -> MaterialTheme.otiumColors.divider
        OtiumButtonStyle.Ghost -> Color.Transparent
    }
    val tintBase = if (inkTinted) ink else accent
    val tint = when {
        !enabled -> Color.Transparent
        pressed -> tintBase.copy(alpha = if (inkTinted) INK_PRESSED else ACCENT_PRESSED)
        hovered -> tintBase.copy(alpha = if (inkTinted) INK_HOVER else ACCENT_HOVER)
        else -> Color.Transparent
    }
    val label = if (inkTinted) ink else MaterialTheme.colorScheme.primary
    return ButtonColors(label, border, tint)
}

@PreviewLightDark
@Composable
private fun OtiumButtonPreview() {
    OtiumTheme {
        PaperBackground {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OtiumButton("Mark as finished", {}, Modifier.fillMaxWidth())
                OtiumButton("+10 pages", {}, style = OtiumButtonStyle.Secondary, compact = true)
                OtiumButton("Read more", {}, style = OtiumButtonStyle.Ghost)
                OtiumButton("Add", {}, icon = Icons.Filled.Add)
                OtiumButton("Back up now", {}, Modifier.fillMaxWidth(), enabled = false)
            }
        }
    }
}
