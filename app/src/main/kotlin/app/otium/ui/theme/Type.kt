package app.otium.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.otium.R

/**
 * Bundled open-licensed fonts (SIL OFL, licenses in third_party/fonts). Each is a variable font
 * file, so one resource covers every weight. No runtime download.
 */
object OtiumFonts {
    private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

    private fun fonts(resId: Int, style: FontStyle = FontStyle.Normal) = weights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            style = style,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }

    /** Cormorant Garamond: display, titles, tabs, buttons and chips. */
    val Display = FontFamily(fonts(R.font.cormorant_garamond))

    /** Lora: running text, authors, hints and small caps labels. Italic maps to its own file. */
    val Text = FontFamily(fonts(R.font.lora) + fonts(R.font.lora_italic, FontStyle.Italic))

    /** Cinzel: the wordmark only. */
    val Wordmark = FontFamily(
        Font(
            resId = R.font.cinzel,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(FontWeight.SemiBold.weight),
            ),
        ),
    )
}

/** Tabular figures for page numbers, counts and percentages. Not for running prose. */
const val TABULAR_FIGURES = "tnum"

private fun display(size: Int, line: Double, weight: FontWeight, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = OtiumFonts.Display,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * line).sp,
        letterSpacing = tracking.em,
    )

private fun text(size: Double, line: Double, tracking: Double = 0.0) = TextStyle(
    fontFamily = OtiumFonts.Text,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
    letterSpacing = tracking.em,
)

/**
 * Cormorant for display, titles and controls; Lora for everything people read. Cormorant is
 * thin when small, so the regular weight is used only at 28 sp and above. Italic (authors,
 * search, hints) and tabular figures are applied where used.
 */
internal val OtiumTypography = Typography(
    // Detail page numeral.
    displayLarge = display(58, line = 1.0, FontWeight.Normal, tracking = -0.02),
    // Reading title.
    displayMedium = display(42, line = 1.0, FontWeight.Normal),
    // Backup and Add titles.
    displaySmall = display(38, line = 1.05, FontWeight.Normal, tracking = -0.01),
    // Index letters and the Reading numeral.
    headlineLarge = display(34, line = 1.05, FontWeight.Normal),
    // Detail title.
    headlineMedium = display(33, line = 1.02, FontWeight.Normal, tracking = -0.02),
    // Shelf names, sheet titles and empty states.
    headlineSmall = display(28, line = 1.1, FontWeight.Normal, tracking = -0.01),
    // Reading entries, dialog titles and screen headers.
    titleLarge = display(22, line = 1.1, FontWeight.SemiBold),
    // Index rows.
    titleMedium = display(19, line = 1.1, FontWeight.SemiBold),
    // Shelf items.
    titleSmall = display(14, line = 1.12, FontWeight.SemiBold),
    // Buttons, chips and tabs.
    labelLarge = display(16, line = 1.25, FontWeight.SemiBold),
    // Navigation labels.
    labelMedium = display(13, line = 1.25, FontWeight.SemiBold),
    // Kickers and small caps labels; set the text in capitals where used.
    labelSmall = text(11.0, line = 1.3, tracking = 0.18),
    bodyLarge = text(15.0, line = 1.5),
    bodyMedium = text(13.5, line = 1.5),
    bodySmall = text(12.0, line = 1.45),
)
