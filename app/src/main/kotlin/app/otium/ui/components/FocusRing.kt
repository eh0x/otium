package app.otium.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val RING_WIDTH = 2.dp
private val RING_OFFSET = 2.dp

/**
 * A 2 dp ring drawn 2 dp outside the element while [focused], for keyboard and switch access.
 * Draws outside the bounds, so apply it before any clip.
 */
internal fun Modifier.focusRing(focused: Boolean, color: Color, cornerRadius: Dp): Modifier =
    drawWithContent {
        drawContent()
        if (focused) {
            val width = RING_WIDTH.toPx()
            val outset = RING_OFFSET.toPx() + width / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(-outset, -outset),
                size = Size(size.width + outset * 2, size.height + outset * 2),
                cornerRadius = CornerRadius(cornerRadius.toPx() + outset),
                style = Stroke(width),
            )
        }
    }
