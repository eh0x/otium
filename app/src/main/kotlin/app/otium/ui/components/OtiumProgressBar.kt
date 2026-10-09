package app.otium.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.otium.R
import app.otium.ui.theme.Motion
import app.otium.ui.theme.otiumColors
import app.otium.ui.theme.otiumMovement
import app.otium.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt

private const val PERCENT = 100

/**
 * One thin accent rule for reading progress. Quiet by design: the fill grows gently (instantly
 * when animations are removed), no streaks or badges.
 * [progress] is a fraction; values outside 0..1 are clamped.
 */
@Composable
fun OtiumProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.otiumColors.statusInProgress,
    trackColor: Color = MaterialTheme.otiumColors.divider,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        targetValue = clamped,
        animationSpec = otiumMovement(rememberReducedMotion(), Motion.PROGRESS_MILLIS),
        label = "progress",
    )
    val description = stringResource(R.string.progress_percent, (clamped * PERCENT).roundToInt())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(trackColor)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                stateDescription = description
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown)
                .background(color),
        )
    }
}
