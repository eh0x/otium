package app.otium.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.ui.theme.Motion
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors
import app.otium.ui.theme.otiumMovement
import app.otium.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt

private const val PERCENT = 100
private val RIBBON_WIDTH = 11.dp
private val RIBBON_HEIGHT = 20.dp
private val RIBBON_RISE = 7.dp

// The ribbon's notch: the bottom centre sits at 76% of its height.
private const val RIBBON_NOTCH = 0.76f

/**
 * A thin accent rule for reading progress, 2 dp by default (3 dp on the detail screen). Quiet by
 * design: the fill grows gently (instantly when animations are removed), no streaks or badges.
 * [showTrack] draws the hairline track; lists show it only for books in progress. [ribbon] adds a
 * bookmark riding the end of the fill; it overhangs the rule, so leave room above and below.
 * [progress] is a fraction; values outside 0..1 are clamped.
 */
@Composable
fun OtiumProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 2.dp,
    showTrack: Boolean = true,
    ribbon: Boolean = false,
    color: Color = MaterialTheme.otiumColors.accent,
    trackColor: Color = MaterialTheme.otiumColors.divider,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        targetValue = clamped,
        animationSpec = otiumMovement(rememberReducedMotion(), Motion.PROGRESS_MILLIS),
        label = "progress",
    )
    val description = stringResource(R.string.progress_percent, (clamped * PERCENT).roundToInt())

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                stateDescription = description
            },
    ) {
        if (showTrack) drawRect(trackColor)
        val end = size.width * shown
        drawRect(color, size = Size(end, size.height))
        if (ribbon) {
            val width = RIBBON_WIDTH.toPx()
            val height = RIBBON_HEIGHT.toPx()
            val left = (end - width / 2).coerceIn(0f, size.width - width)
            val top = -RIBBON_RISE.toPx()
            val bookmark = Path().apply {
                moveTo(left, top)
                lineTo(left + width, top)
                lineTo(left + width, top + height)
                lineTo(left + width / 2, top + height * RIBBON_NOTCH)
                lineTo(left, top + height)
                close()
            }
            drawPath(bookmark, color)
        }
    }
}

@PreviewLightDark
@Composable
private fun OtiumProgressBarPreview() {
    OtiumTheme {
        PaperBackground {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                OtiumProgressBar(progress = 0.4f)
                OtiumProgressBar(progress = 1f, showTrack = false)
                OtiumProgressBar(progress = 0.7f, thickness = 3.dp, ribbon = true)
            }
        }
    }
}
