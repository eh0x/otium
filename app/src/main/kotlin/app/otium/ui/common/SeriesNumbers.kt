package app.otium.ui.common

import java.text.NumberFormat
import java.util.Locale

private const val MAX_FRACTION_DIGITS = 2
private val TYPED_NUMBER = Regex("""\d+(?:[.,]\d+)?""")

/** A book's number in its series as people write it: "2", or "1.5" with the locale's sign. */
fun formatSeriesNumber(number: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = MAX_FRACTION_DIGITS
        isGroupingUsed = false
    }.format(number)

/**
 * The number typed into the series number field, with a point or a comma as the decimal sign;
 * null when the text is not a number of zero or more.
 */
fun parseSeriesNumber(text: String): Double? = text.trim()
    .takeIf(TYPED_NUMBER::matches)
    ?.replace(',', '.')
    ?.toDoubleOrNull()
