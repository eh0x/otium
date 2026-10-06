package app.otium.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens that Material 3 has no slot for. Read them with `MaterialTheme.otiumColors`.
 * Status is never conveyed by color alone: pair these with a label or icon.
 */
@Immutable
class OtiumColors(
    val statusNotStarted: Color,
    val statusInProgress: Color,
    val statusRead: Color,
    val shelfLine: Color,
    val shelfShadow: Color,
    /** Tint of the paper grain; drawn at very low alpha. */
    val paperTint: Color,
)

internal fun OtiumPaletteSpec.toOtiumColors() = OtiumColors(
    statusNotStarted = Color(mutedInk),
    statusInProgress = Color(secondary),
    statusRead = Color(primary),
    shelfLine = Color(shelfLine),
    shelfShadow = Color(shelfShadow),
    paperTint = Color(onBackground),
)

val LocalOtiumColors = staticCompositionLocalOf<OtiumColors> {
    error("OtiumColors not provided. Wrap the content in OtiumTheme.")
}

val MaterialTheme.otiumColors: OtiumColors
    @Composable
    @ReadOnlyComposable
    get() = LocalOtiumColors.current
