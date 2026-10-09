package app.otium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.otium.ui.theme.OtiumTheme
import app.otium.ui.theme.PaperBackground
import app.otium.ui.theme.otiumColors

/** A 1 dp rule in the divider color. Hairlines carry structure in place of elevation. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = MaterialTheme.otiumColors.divider) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

/** Draws a 1 dp rule along the top edge of this element, without taking space. */
fun Modifier.hairlineTop(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawLine(color, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), strokeWidth = stroke)
}

/** Draws a 1 dp rule along the bottom edge of this element, without taking space. */
fun Modifier.hairlineBottom(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    val y = size.height - stroke / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
}

@PreviewLightDark
@Composable
private fun HairlinePreview() {
    OtiumTheme {
        PaperBackground {
            Column(Modifier.padding(22.dp)) {
                Text("Above the rule")
                Hairline(Modifier.padding(vertical = 8.dp))
                Text(
                    "Ruled below",
                    Modifier.hairlineBottom(MaterialTheme.otiumColors.divider),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
