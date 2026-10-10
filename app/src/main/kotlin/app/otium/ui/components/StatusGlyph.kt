package app.otium.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.domain.model.BookStatus
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

private const val HALF_SWEEP_DEGREES = 180f
private const val LEFT_HALF_START_DEGREES = 90f
private val GLYPH_SIZE = 9.dp
private val GLYPH_STROKE = 1.5.dp

@StringRes
fun BookStatus.labelRes(): Int = when (this) {
    BookStatus.NOT_STARTED -> R.string.status_not_started
    BookStatus.IN_PROGRESS -> R.string.status_in_progress
    BookStatus.READ -> R.string.status_read
}

/**
 * A book's status as a small glyph plus a text [label], so it is never conveyed by color alone:
 * a hollow ring when not started, the left half filled while reading, filled when read.
 */
@Composable
fun StatusGlyph(
    status: BookStatus,
    modifier: Modifier = Modifier,
    label: String = stringResource(status.labelRes()),
    labelStyle: TextStyle = MaterialTheme.typography.bodySmall,
    labelColor: Color = MaterialTheme.otiumColors.muted,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status = status)
        Text(text = label, style = labelStyle, color = labelColor)
    }
}

/** The status glyph alone. Decorative: always place a text label beside it. */
@Composable
fun StatusDot(status: BookStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.otiumColors
    val color = when (status) {
        BookStatus.NOT_STARTED -> colors.statusNotStarted
        BookStatus.IN_PROGRESS -> colors.statusInProgress
        BookStatus.READ -> colors.statusRead
    }
    Canvas(modifier = modifier.size(GLYPH_SIZE)) {
        val stroke = GLYPH_STROKE.toPx()
        val ring = Stroke(width = stroke)
        val radius = (size.minDimension - stroke) / 2
        when (status) {
            BookStatus.NOT_STARTED -> drawCircle(color = color, radius = radius, style = ring)

            BookStatus.IN_PROGRESS -> {
                drawCircle(color = color, radius = radius, style = ring)
                drawArc(
                    color = color,
                    startAngle = LEFT_HALF_START_DEGREES,
                    sweepAngle = HALF_SWEEP_DEGREES,
                    useCenter = true,
                )
            }

            BookStatus.READ -> drawCircle(color = color)
        }
    }
}

@PreviewLightDark
@Composable
private fun StatusGlyphPreview() {
    OtiumTheme {
        PaperBackground {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookStatus.entries.forEach { StatusGlyph(status = it) }
                StatusGlyph(
                    status = BookStatus.IN_PROGRESS,
                    label = stringResource(R.string.status_in_progress).uppercase(),
                    labelStyle = MaterialTheme.typography.labelSmall,
                    labelColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
