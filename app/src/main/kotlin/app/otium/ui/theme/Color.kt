package app.otium.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The Classical palette: cool neutral paper, warm ink and one gold accent. This is the only file
// in the UI layer that contains raw color values (enforced by ArchitectureTest); everything else
// reads tokens from the theme. The window background in res/values*/colors.xml repeats the
// background value and is checked against it by OtiumThemeTest.
//
// Values are WCAG-checked by PaletteContrastTest. Two roles deviate from the design handoff:
//   muted   #676665 instead of #6C6B6A, which was 4.39:1 on the surface; now 4.73:1
//   divider is the opaque blend of ink at 16% on the background, so it reads the same on every
//           ground and can be tested
// The accent (#B68235 light) is about 3:1 on the background: use it for strokes, rules, glyphs,
// icons and type of 24 sp or more, on the background or sheet only. Small accent-family text uses
// primary (gold), which reaches 4.5:1.

/** ARGB values for one theme. Plain numbers, so contrast can be tested without Compose. */
internal data class OtiumPaletteSpec(
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    /** Sheets, dialogs and menus. */
    val sheet: Long,
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
    /** Quiet secondary text, the "not started" status and inactive icons. */
    val muted: Long,
    /** Hairlines: the 1 dp rules that carry structure in place of elevation. */
    val divider: Long,
    /** Strokes, rules, glyphs, icons and large display type. Not for small text. */
    val accent: Long,
    val scrim: Long,
    val coverShadow: Long,
)

internal val LightPalette = OtiumPaletteSpec(
    background = 0xFFF3F2F2,
    onBackground = 0xFF201F1D,
    surface = 0xFFEAE9E9,
    onSurface = 0xFF201F1D,
    sheet = 0xFFFAF9F8,
    primary = 0xFF7D5411, // Gold, the text-safe accent
    onPrimary = 0xFFF3F2F2,
    primaryContainer = 0xFFEAE2D8, // Accent at 14% on the background
    onPrimaryContainer = 0xFF7D5411,
    error = 0xFFB3261E,
    onError = 0xFFFFFFFF,
    errorContainer = 0xFFF9DEDC,
    onErrorContainer = 0xFF410E0B,
    muted = 0xFF676665,
    divider = 0xFFD1D0D0,
    accent = 0xFFB68235,
    scrim = 0x6B201F1D,
    coverShadow = 0x47201F1D,
)

/** "Lamplight": a warm dark, not pure black. */
internal val DarkPalette = OtiumPaletteSpec(
    background = 0xFF161513,
    onBackground = 0xFFECE5D6,
    surface = 0xFF242220,
    onSurface = 0xFFECE5D6,
    sheet = 0xFF1D1B19,
    primary = 0xFFE1AD66,
    onPrimary = 0xFF161513,
    primaryContainer = 0xFF312A1D,
    onPrimaryContainer = 0xFFE1AD66,
    error = 0xFFF2B8B5,
    onError = 0xFF601410,
    errorContainer = 0xFF8C1D18,
    onErrorContainer = 0xFFF9DEDC,
    muted = 0xFFA39E94,
    divider = 0xFF383632,
    accent = 0xFFD9A85A,
    scrim = 0x99000000,
    coverShadow = 0x66000000,
)

/** Text and ornament on generated covers. The same in both themes, since boards are dark. */
internal const val COVER_TEXT = 0xFFF3ECDD
internal const val COVER_ORNAMENT = 0xFFE1AD66

/** Spine shading across a generated cover, left to right: shade, clear, highlight, clear. */
internal val CoverSpine = listOf(0x4D000000L, 0x00000000L, 0x17FFFFFFL, 0x00000000L)

/** The faint top-to-bottom sheen over a generated cover. */
internal val CoverSheen = listOf(0x0DFFFFFFL, 0x24000000L)

/**
 * Board colors for generated covers: deep, muted tones spread around the hue circle (OKLCH
 * lightness 0.33 to 0.44, chroma 0.05 to 0.09, kept in the sRGB gamut). A stable hash of the
 * title picks one. Every board keeps [COVER_TEXT] at 4.5:1 or more (PaletteContrastTest).
 */
internal val CoverBoards = listOf(
    0xFF582F33,
    0xFF713E26,
    0xFF493006,
    0xFF4E4A09,
    0xFF37482D,
    0xFF275F49,
    0xFF034747,
    0xFF0D556C,
    0xFF1A3658,
    0xFF433F76,
    0xFF4C3A55,
    0xFF6E425B,
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
        // No second hue: secondary and tertiary are the ink and muted neutrals.
        secondary = Color(onBackground),
        onSecondary = Color(background),
        secondaryContainer = Color(surface),
        onSecondaryContainer = Color(onSurface),
        tertiary = Color(muted),
        onTertiary = Color(background),
        tertiaryContainer = Color(surface),
        onTertiaryContainer = Color(onSurface),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surface),
        onSurfaceVariant = Color(muted),
        surfaceTint = Color(primary),
        inverseSurface = Color(onBackground),
        inverseOnSurface = Color(background),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(muted),
        outlineVariant = Color(divider),
        scrim = Color(scrim),
        surfaceBright = Color(sheet),
        surfaceDim = Color(surface),
        // Sheets (Low), menus (Container) and dialogs (High) use the sheet fill; filled inputs
        // (Highest) use the surface mat.
        surfaceContainerLowest = Color(sheet),
        surfaceContainerLow = Color(sheet),
        surfaceContainer = Color(sheet),
        surfaceContainerHigh = Color(sheet),
        surfaceContainerHighest = Color(surface),
    )
}
