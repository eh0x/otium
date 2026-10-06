package app.otium.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The Earth & Ink palette. This is the only file in the UI layer that contains raw color
// values (enforced by ArchitectureTest); everything else reads tokens from the theme.
// The window background in res/values*/colors.xml repeats the background value and is
// checked against it by OtiumThemeTest.
//
// Values are WCAG-checked by PaletteContrastTest. Against the first proposal in the design doc,
// Sage and Terra are darker so they reach 4.5:1 as text and as button backgrounds.
//   Parchment #F4EBD9 / Ink #1F2433          13.1:1
//   Sage      #566A4B on Parchment            5.0:1 (proposal #6F8062 was 3.6:1)
//   Terra     #A24A2D on Parchment            5.0:1 (proposal #B5573A was 4.0:1)

/** ARGB values for one theme. Plain numbers, so contrast can be tested without Compose. */
internal data class OtiumPaletteSpec(
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val surfaceContainerLow: Long,
    val surfaceContainer: Long,
    val surfaceContainerHigh: Long,
    val surfaceContainerHighest: Long,
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val tertiary: Long,
    val onTertiary: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,
    val outline: Long,
    val outlineVariant: Long,
    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
    /** Muted ink: the "not started" status and quiet secondary text. */
    val mutedInk: Long,
    val shelfLine: Long,
    val shelfShadow: Long,
)

internal val LightPalette = OtiumPaletteSpec(
    background = 0xFFF4EBD9, // Parchment
    onBackground = 0xFF1F2433, // Ink
    surface = 0xFFFBF6EA,
    onSurface = 0xFF1F2433,
    surfaceVariant = 0xFFE8DEC7,
    onSurfaceVariant = 0xFF4A4A52,
    surfaceContainerLow = 0xFFF8F1E1,
    surfaceContainer = 0xFFEFE5CF,
    surfaceContainerHigh = 0xFFEBDFC6,
    surfaceContainerHighest = 0xFFE2D6BC,
    primary = 0xFF566A4B, // Tiber Sage
    onPrimary = 0xFFFBF6EA,
    primaryContainer = 0xFFDCE5D0,
    onPrimaryContainer = 0xFF25301E,
    secondary = 0xFFA24A2D, // Terra
    onSecondary = 0xFFFBF6EA,
    secondaryContainer = 0xFFF3D9CB,
    onSecondaryContainer = 0xFF4A2012,
    tertiary = 0xFF5A5E6B,
    onTertiary = 0xFFFBF6EA,
    tertiaryContainer = 0xFFE5DFD0,
    onTertiaryContainer = 0xFF262A36,
    outline = 0xFF7A7466,
    outlineVariant = 0xFFCFC4AA,
    error = 0xFFB3261E,
    onError = 0xFFFFFFFF,
    errorContainer = 0xFFF9DEDC,
    onErrorContainer = 0xFF410E0B,
    mutedInk = 0xFF5A5E6B,
    shelfLine = 0xFF8A6B4A,
    shelfShadow = 0x331F2433,
)

/** A warm "lamp-lit study" dark, not pure black. */
internal val DarkPalette = OtiumPaletteSpec(
    background = 0xFF1B1A17,
    onBackground = 0xFFEDE3CF,
    surface = 0xFF24221E,
    onSurface = 0xFFEDE3CF,
    surfaceVariant = 0xFF3A372F,
    onSurfaceVariant = 0xFFCFC6B3,
    surfaceContainerLow = 0xFF201F1B,
    surfaceContainer = 0xFF2A2823,
    surfaceContainerHigh = 0xFF33312B,
    surfaceContainerHighest = 0xFF3D3A33,
    primary = 0xFFA9BD9B,
    onPrimary = 0xFF1D2A16,
    primaryContainer = 0xFF3A4B30,
    onPrimaryContainer = 0xFFDCE9D0,
    secondary = 0xFFE3946F,
    onSecondary = 0xFF3A1608,
    secondaryContainer = 0xFF6A2E18,
    onSecondaryContainer = 0xFFFBDCCB,
    tertiary = 0xFFB5AE9E,
    onTertiary = 0xFF24221E,
    tertiaryContainer = 0xFF3D3B35,
    onTertiaryContainer = 0xFFE0DCCF,
    outline = 0xFF8F8878,
    outlineVariant = 0xFF4A463C,
    error = 0xFFF2B8B5,
    onError = 0xFF601410,
    errorContainer = 0xFF8C1D18,
    onErrorContainer = 0xFFF9DEDC,
    mutedInk = 0xFFB5AE9E,
    shelfLine = 0xFF5A4630,
    shelfShadow = 0x66000000,
)

/**
 * Maps a palette onto Material 3. Starts from the stock scheme only so that rarely used slots
 * have a value; every slot the app uses comes from the palette.
 */
internal fun OtiumPaletteSpec.toColorScheme(dark: Boolean): ColorScheme {
    val stock = if (dark) darkColorScheme() else lightColorScheme()
    val counterpart = if (dark) LightPalette else DarkPalette
    return stock.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(counterpart.primary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        inverseSurface = Color(counterpart.surface),
        inverseOnSurface = Color(counterpart.onSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color.Black,
        surfaceBright = Color(surface),
        surfaceDim = Color(surfaceContainerHigh),
        surfaceContainerLowest = Color(surface),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
    )
}
