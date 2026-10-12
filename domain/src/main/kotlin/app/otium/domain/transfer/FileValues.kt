package app.otium.domain.transfer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Small conversions shared by the library file formats. Dates are days in UTC. */
internal object FileValues {
    private val DATE = Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})""")

    /** First characters that make a spreadsheet read a cell as a formula (OWASP, CSV injection). */
    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

    /**
     * [value] as a cell no spreadsheet runs as a formula: one starting with `=`, `+`, `-`, `@`, a
     * tab or a carriage return gets a leading apostrophe, which spreadsheets show as text.
     */
    fun spreadsheetSafe(value: String): String =
        if (value.firstOrNull() in FORMULA_STARTS) "'$value" else value

    /** Undoes [spreadsheetSafe], so an exported title reads back as it was. */
    fun fromSpreadsheetSafe(value: String): String =
        if (value.length > 1 && value[0] == '\'' && value[1] in FORMULA_STARTS) {
            value.substring(1)
        } else {
            value
        }

    /** Epoch milliseconds at the start of the day written as `2024/03/05` or `2024-03-05`. */
    fun parseDate(value: String): Long? {
        val (year, month, day) = DATE.find(value)?.destructured ?: return null
        return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }
            .getOrNull()
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
    }

    /** [millis] as `2024/03/05`, the way Goodreads writes dates. */
    fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
            .replace('-', '/')

    /** Digits and a check `X` only, so `="0306406152"` and `978-0-306` read as ISBNs. */
    fun isbnOrNull(value: String): String? =
        value.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase().ifEmpty { null }

    fun positiveIntOrNull(value: String): Int? = value.toIntOrNull()?.takeIf { it > 0 }

    /** The names in a comma-separated list such as `to-read, favourites`, without blanks. */
    fun names(value: String): List<String> =
        value.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
}
