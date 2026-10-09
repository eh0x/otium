package app.otium.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors
import app.otium.ui.theme.rememberReducedMotion

private const val SEGMENT = 0.4f
private const val LOOP_MILLIS = 1100

/**
 * Work of unknown length: a 2 dp hairline track with an accent segment sliding across it. With
 * animations removed the segment rests at the start, so the rule still shows that work is going on.
 */
@Composable
fun OtiumIndeterminateRule(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.otiumColors
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "indeterminate rule")
    val start by transition.animateFloat(
        initialValue = -SEGMENT,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(LOOP_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "segment",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        drawRect(colors.divider)
        val from = if (reduced) 0f else start.coerceAtLeast(0f)
        val to = if (reduced) SEGMENT else (start + SEGMENT).coerceAtMost(1f)
        if (to > from) {
            drawRect(
                color = colors.accent,
                topLeft = Offset(size.width * from, 0f),
                size = Size(size.width * (to - from), size.height),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OtiumIndeterminateRulePreview() {
    OtiumTheme { PaperBackground { OtiumIndeterminateRule(Modifier.padding(22.dp)) } }
}
