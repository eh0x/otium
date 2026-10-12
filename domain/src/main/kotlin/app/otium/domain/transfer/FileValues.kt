package app.otium.domain.transfer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Small conversions shared by the library file formats. Dates are days in UTC. */
internal object FileValues {
    private val DATE = Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})""")

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
