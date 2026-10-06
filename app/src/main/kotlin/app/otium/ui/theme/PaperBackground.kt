package app.otium.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import kotlin.random.Random

private const val TILE_SIZE = 64
private const val GRAIN_COUNT = 700
private const val GRAIN_MAX_ALPHA = 0.045f
private const val GRAIN_SEED = 7

/**
 * A flat parchment surface with a very subtle paper grain. The grain is a tiny tile generated
 * once in code (no image asset), repeated across the area. If it were ever dropped, the flat
 * background color alone is already correct.
 */
@Composable
fun PaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val grain = MaterialTheme.otiumColors.paperTint
    val brush = remember(grain) {
        ShaderBrush(ImageShader(grainTile(grain), TileMode.Repeated, TileMode.Repeated))
    }
    // Like Surface, provide the matching content color. Without it, text falls back to black,
    // which is unreadable on the dark background.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.background)
                .drawBehind { drawRect(brush) },
            content = content,
        )
    }
}

private fun grainTile(tint: Color): ImageBitmap {
    val tile = ImageBitmap(TILE_SIZE, TILE_SIZE)
    val canvas = Canvas(tile)
    val paint = Paint()
    val random = Random(GRAIN_SEED)
    repeat(GRAIN_COUNT) {
        paint.color = tint.copy(alpha = random.nextFloat() * GRAIN_MAX_ALPHA)
        val origin =
            Offset(random.nextInt(TILE_SIZE).toFloat(), random.nextInt(TILE_SIZE).toFloat())
        canvas.drawRect(origin.x, origin.y, origin.x + 1f, origin.y + 1f, paint)
    }
    return tile
}
