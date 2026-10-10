package app.otium.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

// The launcher icon's geometry (res/drawable/ic_launcher_foreground.xml), cropped from its
// 108-unit canvas to the 48 units around the ring.
private const val MARK_UNITS = 48f
private const val RING_CENTRE = 24f
private const val RING_RADIUS = 22f
private const val RING_STROKE = 3.4f
private const val RIBBON_LEFT = 19f
private const val RIBBON_RIGHT = 29f
private const val RIBBON_TOP = 2f
private const val RIBBON_BOTTOM = 30f
private const val RIBBON_NOTCH = 25f

/**
 * The "Ribbon O" mark: a ring with the progress bookmark hanging inside its top rim, as on the
 * launcher icon. Decorative, so it has no content description; place it beside the wordmark.
 */
@Composable
fun OtiumMark(
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    ringColor: Color = MaterialTheme.colorScheme.onBackground,
    ribbonColor: Color = MaterialTheme.otiumColors.accent,
) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / MARK_UNITS
        drawCircle(
            color = ringColor,
            radius = RING_RADIUS * unit,
            center = Offset(RING_CENTRE * unit, RING_CENTRE * unit),
            style = Stroke(width = RING_STROKE * unit),
        )
        val ribbon = Path().apply {
            moveTo(RIBBON_LEFT * unit, RIBBON_TOP * unit)
            lineTo(RIBBON_RIGHT * unit, RIBBON_TOP * unit)
            lineTo(RIBBON_RIGHT * unit, RIBBON_BOTTOM * unit)
            lineTo(RING_CENTRE * unit, RIBBON_NOTCH * unit)
            lineTo(RIBBON_LEFT * unit, RIBBON_BOTTOM * unit)
            close()
        }
        drawPath(path = ribbon, color = ribbonColor)
    }
}

@PreviewLightDark
@Composable
private fun OtiumMarkPreview() {
    OtiumTheme { PaperBackground { OtiumMark() } }
}
