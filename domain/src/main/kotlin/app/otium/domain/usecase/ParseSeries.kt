package app.otium.domain.usecase

import app.otium.domain.model.BookCandidate

/** A series read from free text. The name may be missing; the number never is. */
data class SeriesGuess(val name: String?, val index: Double)

/**
 * Reads a book's series and its number from free text, for prefilling the confirm form. Neither
 * Google Books nor Open Library has a structured series field, so this is a guess the owner
 * confirms or corrects.
 *
 * It is deliberately cautious. In a subtitle or title a number only counts after a keyword
 * ("Book 2", "Vol. 3", "#3"), so "1984" and "Catch-22" are never read as series numbers. Open
 * Library's `series` entries, which name a series by definition, also accept the catalogue
 * spellings "Discworld ; 12", "Discworld (12)" and "Discworld -- 12".
 */
object ParseSeries {

    private const val NUMBER = "(\\d+(?:[.,]\\d+)?|one|two|three|four|five|six|seven|eight|nine" +
        "|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen" +
        "|twenty|[ivx]+)"

    /** "Book", "Vol." and the like, or a number sign. */
    private const val MARK = "(?:\\b(?:book|volume|vol\\.?|no\\.)\\s*|#\\s*)"
    private const val SEPARATOR = "[,:;\\u2013\\u2014-]"

    /** "Book 2 of The Expanse". */
    private val NUMBER_OF_NAME = pattern("^$MARK$NUMBER\\s+(?:of|in)\\s+(.+)$")

    /** "(The Expanse, Book 1)", "(Book 2)" or "(Discworld #3)", anywhere in the text. */
    private val BRACKETED =
        pattern("[(\\[]\\s*(?:([^()\\[\\]]*?)\\s*$SEPARATOR?\\s*)?$MARK$NUMBER\\s*[)\\]]")

    /** "A Song of Ice and Fire, Book 1", "Discworld #3" or "Book 2" as the whole text. */
    private val TRAILING = pattern("^(?:(.+?)\\s*$SEPARATOR?\\s+)?$MARK$NUMBER$")

    /** "Discworld ; 12" or "Discworld ; v. 12", the library catalogue form. */
    private val CATALOGUE =
        pattern("^([^;]+?)\\s*;\\s*(?:v\\.|vol\\.?|no\\.|book|#)?\\s*$NUMBER$")

    /** "Dune (1)". */
    private val NAME_PAREN_NUMBER = pattern("^(.+?)\\s*\\($NUMBER\\)$")

    /** "Discworld -- 12". */
    private val NAME_DASHES_NUMBER = pattern("^(.+?)\\s*--\\s*$NUMBER$")

    private val TRAILING_SERIES_WORD = Regex("\\s+series$", RegexOption.IGNORE_CASE)
    private val EDGE_PUNCTUATION = Regex("^[\\s,:;\\u2013\\u2014-]+|[\\s,:;\\u2013\\u2014-]+$")

    private val WORDS = listOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen", "twenty",
    )
    private val ROMAN = mapOf('i' to 1, 'v' to 5, 'x' to 10)
    private const val MAX_ROMAN = 39

    /** The series a subtitle or title names, or null when it names none. */
    fun fromText(text: String?): SeriesGuess? {
        val trimmed = text?.trim()?.takeIf(String::isNotEmpty) ?: return null
        NUMBER_OF_NAME.find(trimmed)?.let { match ->
            val name = cleanName(match.groupValues[2]) ?: return null
            return guess(name, match.groupValues[1])
        }
        return listOf(BRACKETED, TRAILING).firstNotNullOfOrNull { regex ->
            regex.find(trimmed)?.let { guess(cleanName(it.groupValues[1]), it.groupValues[2]) }
        }
    }

    /**
     * The series in Open Library's `series` entries: the first one that gives both a name and a
     * number. An entry may hold several spellings separated by semicolons, as in
     * "Dune (1); Dune Chronicles, Book 1". Unnumbered entries are often a publisher's line
     * ("Penguin modern classics") rather than a story's series, so they are ignored.
     */
    fun fromCatalogue(entries: List<String>): SeriesGuess? = entries.firstNotNullOfOrNull { entry ->
        val whole = entry.trim()
        CATALOGUE.find(whole)?.let { guess(cleanName(it.groupValues[1]), it.groupValues[2]) }
            ?.takeIf { it.name != null }
            ?: whole.split(';').firstNotNullOfOrNull(::fromCatalogueSpelling)
    }

    private fun fromCatalogueSpelling(spelling: String): SeriesGuess? {
        val trimmed = spelling.trim()
        val guess = listOf(NAME_PAREN_NUMBER, NAME_DASHES_NUMBER).firstNotNullOfOrNull { regex ->
            regex.find(trimmed)?.let { guess(cleanName(it.groupValues[1]), it.groupValues[2]) }
        } ?: fromText(trimmed)
        return guess?.takeIf { it.name != null }
    }

    private fun guess(name: String?, number: String): SeriesGuess? =
        number(number)?.let { SeriesGuess(name, it) }

    /** The name without edge punctuation or a trailing "series"; null unless it has a letter. */
    private fun cleanName(raw: String): String? = raw
        .replace(EDGE_PUNCTUATION, "")
        .replace(TRAILING_SERIES_WORD, "")
        .trim()
        .takeIf { name -> name.any(Char::isLetter) }

    private fun number(text: String): Double? {
        val lower = text.lowercase()
        val wordIndex = WORDS.indexOf(lower)
        return when {
            wordIndex >= 0 -> wordIndex + 1.0
            lower.first().isDigit() -> lower.replace(',', '.').toDoubleOrNull()
            else -> roman(lower)?.toDouble()
        }
    }

    /** Small Roman numerals (I to XXXIX), as used for volumes; anything else is not a number. */
    private fun roman(text: String): Int? {
        val values = text.map { ROMAN[it] ?: return null }
        val total = values.indices.sumOf { i ->
            val value = values[i]
            if (i + 1 < values.size && values[i + 1] > value) -value else value
        }
        return total.takeIf { it in 1..MAX_ROMAN }
    }

    private fun pattern(regex: String) = Regex(regex, RegexOption.IGNORE_CASE)
}

/**
 * The candidate with a series guessed from its subtitle, then its title, unless it already names
 * one. The subtitle and title are left as they are.
 */
fun BookCandidate.withSeriesGuess(): BookCandidate {
    if (!series.isNullOrBlank() || seriesIndex != null) return this
    val guess = ParseSeries.fromText(subtitle) ?: ParseSeries.fromText(title) ?: return this
    return copy(series = guess.name, seriesIndex = guess.index)
}
