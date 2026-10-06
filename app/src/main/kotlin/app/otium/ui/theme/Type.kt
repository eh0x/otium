package app.otium.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.otium.R

/**
 * Bundled open-licensed fonts (SIL OFL, licenses in third_party/fonts). Each is a variable font
 * file, so one resource covers every weight. No runtime download.
 */
object OtiumFonts {
    private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

    private fun family(resId: Int, weights: List<FontWeight>) = FontFamily(
        weights.map { weight ->
            Font(
                resId = resId,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

    /** EB Garamond: headlines and screen titles. */
    val Serif = family(R.font.eb_garamond, weights)

    /** Source Sans 3: lists, filters, forms. */
    val Sans = family(R.font.source_sans_3, weights)

    /** Cinzel: the wordmark only. */
    val Wordmark = family(R.font.cinzel, listOf(FontWeight.SemiBold))
}

private val stock = Typography()

/** Serif for display, headline and large titles; sans for everything people read and tap. */
internal val OtiumTypography = Typography(
    displayLarge = stock.displayLarge.copy(fontFamily = OtiumFonts.Serif),
    displayMedium = stock.displayMedium.copy(fontFamily = OtiumFonts.Serif),
    displaySmall = stock.displaySmall.copy(fontFamily = OtiumFonts.Serif),
    headlineLarge = stock.headlineLarge.copy(
        fontFamily = OtiumFonts.Serif,
        fontSize = 34.sp,
        lineHeight = 42.sp,
    ),
    headlineMedium = stock.headlineMedium.copy(
        fontFamily = OtiumFonts.Serif,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    headlineSmall = stock.headlineSmall.copy(
        fontFamily = OtiumFonts.Serif,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = stock.titleLarge.copy(
        fontFamily = OtiumFonts.Serif,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = stock.titleMedium.copy(fontFamily = OtiumFonts.Sans),
    titleSmall = stock.titleSmall.copy(fontFamily = OtiumFonts.Sans),
    bodyLarge = stock.bodyLarge.copy(fontFamily = OtiumFonts.Sans),
    bodyMedium = stock.bodyMedium.copy(fontFamily = OtiumFonts.Sans),
    bodySmall = stock.bodySmall.copy(fontFamily = OtiumFonts.Sans),
    labelLarge = stock.labelLarge.copy(fontFamily = OtiumFonts.Sans),
    labelMedium = stock.labelMedium.copy(fontFamily = OtiumFonts.Sans),
    labelSmall = stock.labelSmall.copy(fontFamily = OtiumFonts.Sans),
)
