package app.otium.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.otium.ui.theme.Motion
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors
import app.otium.ui.theme.otiumMovement
import app.otium.ui.theme.rememberReducedMotion

private const val ON_FILL_ALPHA = 0.16f
private const val DISABLED_ALPHA = 0.45f
private val TRACK_WIDTH = 42.dp
private val TRACK_HEIGHT = 24.dp
private val KNOB = 16.dp
private val KNOB_INSET = 4.dp

/**
 * The Otium switch: a hairline track with a small knob; on adds an accent border, a faint accent
 * fill and an accent knob. Touch area 48 dp. Pass a null [onCheckedChange] when a surrounding row
 * is the toggleable target (as in a labelled settings row), so the row is one control for screen
 * readers.
 */
@Composable
fun OtiumSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.otiumColors
    val knobOffset by animateDpAsState(
        targetValue = if (checked) TRACK_WIDTH - KNOB - KNOB_INSET else KNOB_INSET,
        animationSpec = otiumMovement(rememberReducedMotion(), Motion.SWITCH_MILLIS),
        label = "switch knob",
    )
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .then(toggle)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(TRACK_WIDTH, TRACK_HEIGHT)
                .background(
                    if (checked) colors.accent.copy(alpha = ON_FILL_ALPHA) else Color.Transparent,
                    CircleShape,
                )
                .border(1.dp, if (checked) colors.accent else colors.divider, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(knobOffset.roundToPx(), 0) }
                    .size(KNOB)
                    .background(if (checked) colors.accent else colors.muted, CircleShape),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OtiumSwitchPreview() {
    var on by remember { mutableStateOf(true) }
    OtiumTheme {
        PaperBackground {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .toggleable(value = on, role = Role.Switch, onValueChange = { on = it }),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Encrypt backups", style = MaterialTheme.typography.titleLarge)
                OtiumSwitch(checked = on, onCheckedChange = null)
            }
        }
    }
}
