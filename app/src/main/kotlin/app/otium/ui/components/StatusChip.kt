package app.otium.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.BookStatus
import app.otium.ui.theme.otiumColors

private const val STROKE_FRACTION = 0.14f
private const val HALF_SWEEP_DEGREES = 180f
private const val TOP_START_DEGREES = -90f

// Check mark vertices as fractions of the icon size.
private const val CHECK_START_X = 0.28f
private const val CHECK_START_Y = 0.52f
private const val CHECK_MID_X = 0.44f
private const val CHECK_MID_Y = 0.68f
private const val CHECK_END_X = 0.74f
private const val CHECK_END_Y = 0.34f

/**
 * Shows a book's status as an icon plus a text label, so it is never conveyed by color alone.
 * The icon is drawn in code: an empty circle, a half filled circle and a checked circle.
 */
@StringRes
fun BookStatus.labelRes(): Int = when (this) {
    BookStatus.NOT_STARTED -> R.string.status_not_started
    BookStatus.IN_PROGRESS -> R.string.status_in_progress
    BookStatus.READ -> R.string.status_read
}

@Composable
fun StatusChip(status: BookStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.otiumColors
    val color = when (status) {
        BookStatus.NOT_STARTED -> colors.statusNotStarted
        BookStatus.IN_PROGRESS -> colors.statusInProgress
        BookStatus.READ -> colors.statusRead
    }
    val label = stringResource(status.labelRes())

    Row(
        modifier = modifier
            .border(1.dp, color, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(
            status = status,
            color = color,
            checkColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.size(12.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun StatusIcon(
    status: BookStatus,
    color: Color,
    checkColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * STROKE_FRACTION)
        when (status) {
            BookStatus.NOT_STARTED -> drawCircle(color = color, style = stroke)

            BookStatus.IN_PROGRESS -> {
                drawCircle(color = color, style = stroke)
                drawArc(
                    color = color,
                    startAngle = TOP_START_DEGREES,
                    sweepAngle = HALF_SWEEP_DEGREES,
                    useCenter = true,
                )
            }

            BookStatus.READ -> {
                drawCircle(color = color)
                val check = Path().apply {
                    moveTo(size.width * CHECK_START_X, size.height * CHECK_START_Y)
                    lineTo(size.width * CHECK_MID_X, size.height * CHECK_MID_Y)
                    lineTo(size.width * CHECK_END_X, size.height * CHECK_END_Y)
                }
                drawPath(
                    path = check,
                    color = checkColor,
                    style = Stroke(
                        width = stroke.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}
