package app.otium.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

private const val LARGE_FONT_SCALE = 1.5f

/**
 * True when the user has made text much larger (150% or more). Side-by-side layouts use it to
 * stack instead, so text gets the full width rather than breaking inside words.
 */
@Composable
@ReadOnlyComposable
fun isLargeFontScale(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
