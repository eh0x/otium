package app.otium.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens that Material 3 has no slot for. Read them with `MaterialTheme.otiumColors`.
 * Status is never conveyed by color alone: pair these with a label or glyph.
 */
@Immutable
class OtiumColors(
    /** Strokes, rules, glyphs, icons and type of 24 sp or more. Small text uses `primary`. */
    val accent: Color,
    /** Quiet secondary text and inactive icons. */
    val muted: Color,
    /** 1 dp hairlines. */
    val divider: Color,
    /** Fill of sheets, dialogs and menus. */
    val sheet: Color,
    val scrim: Color,
    val cover: CoverColors,
) {
    val statusNotStarted: Color get() = muted
    val statusInProgress: Color get() = accent
    val statusRead: Color get() = accent
}

/** Colors of book covers, real and generated. */
@Immutable
class CoverColors(
    /** The soft shadow under every cover. */
    val shadow: Color,
    /** Title and author on generated covers. */
    val text: Color,
    /** The small gold ornament on generated covers. */
    val ornament: Color,
    /** Board colors for generated covers; pick one by a stable hash of the title. */
    val boards: List<Color>,
)

internal fun OtiumPaletteSpec.toOtiumColors() = OtiumColors(
    accent = Color(accent),
    muted = Color(muted),
    divider = Color(divider),
    sheet = Color(sheet),
    scrim = Color(scrim),
    cover = CoverColors(
        shadow = Color(coverShadow),
        text = Color(COVER_TEXT),
        ornament = Color(COVER_ORNAMENT),
        boards = CoverBoards.map(::Color),
    ),
)

val LocalOtiumColors = staticCompositionLocalOf<OtiumColors> {
    error("OtiumColors not provided. Wrap the content in OtiumTheme.")
}

val MaterialTheme.otiumColors: OtiumColors
    @Composable
    @ReadOnlyComposable
    get() = LocalOtiumColors.current
