package app.otium.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The Otium visual identity: the Classical palette (paper, ink and one gold accent) in light
 * and a warm "lamplight" dark.
 * Dynamic color is deliberately off, because it would override the palette.
 * Screens read tokens only (`MaterialTheme.colorScheme`, `.typography`, `.shapes`,
 * `.otiumColors`) and never raw colors.
 */
@Composable
fun OtiumTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalOtiumColors provides palette.toOtiumColors()) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(dark = darkTheme),
            typography = OtiumTypography,
            shapes = OtiumShapes,
            content = content,
        )
    }
}
