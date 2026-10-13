package app.otium.domain.usecase

import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseSeriesTest {

    @Test
    fun `subtitle and title patterns give the series and its number`() {
        val cases = mapOf(
            "(Book 2)" to SeriesGuess(null, 2.0),
            "Book 2" to SeriesGuess(null, 2.0),
            "(The Expanse, Book 1)" to SeriesGuess("The Expanse", 1.0),
            "Leviathan Wakes (The Expanse, Book 1)" to SeriesGuess("The Expanse", 1.0),
            "Book 2 of The Expanse" to SeriesGuess("The Expanse", 2.0),
            "Book Two of the Wheel of Time series" to SeriesGuess("the Wheel of Time", 2.0),
            "(Discworld #3)" to SeriesGuess("Discworld", 3.0),
            "(Discworld, #3)" to SeriesGuess("Discworld", 3.0),
            "Discworld #3" to SeriesGuess("Discworld", 3.0),
            "A Song of Ice and Fire, Book 1" to SeriesGuess("A Song of Ice and Fire", 1.0),
            "Vol. 3" to SeriesGuess(null, 3.0),
            "Volume 4" to SeriesGuess(null, 4.0),
            "[Wayfarers, Book 2]" to SeriesGuess("Wayfarers", 2.0),
            "(The Murderbot Diaries, Book 4.5)" to SeriesGuess("The Murderbot Diaries", 4.5),
            "(Book 1,5)" to SeriesGuess(null, 1.5),
            "Book III" to SeriesGuess(null, 3.0),
        )
        cases.forEach { (text, expected) ->
            assertEquals(text, expected, ParseSeries.fromText(text))
        }
    }

    @Test
    fun `text without a series keyword gives nothing`() {
        listOf(
            null,
            "",
            "1984",
            "Catch-22",
            "A Novel",
            "Part One",
            "The Book of the New Sun",
            "Book of Days",
            "Fahrenheit 451",
            "Book 2 of 3",
        ).forEach { assertNull(it, ParseSeries.fromText(it)) }
    }

    @Test
    fun `catalogue entries read the recorded open library string`() {
        val guess = ParseSeries.fromCatalogue(listOf("Dune (1); Dune Chronicles, Book 1"))

        assertEquals(SeriesGuess("Dune", 1.0), guess)
    }

    @Test
    fun `catalogue entries accept the library catalogue spellings`() {
        val cases = mapOf(
            "Discworld ; 12" to SeriesGuess("Discworld", 12.0),
            "Discworld ; v. 12" to SeriesGuess("Discworld", 12.0),
            "Discworld -- 12" to SeriesGuess("Discworld", 12.0),
            "Discworld (12)" to SeriesGuess("Discworld", 12.0),
            "Discworld #12" to SeriesGuess("Discworld", 12.0),
            "Discworld, Book 12" to SeriesGuess("Discworld", 12.0),
        )
        cases.forEach { (entry, expected) ->
            assertEquals(entry, expected, ParseSeries.fromCatalogue(listOf(entry)))
        }
    }

    @Test
    fun `catalogue entries without a number are ignored`() {
        assertNull(ParseSeries.fromCatalogue(listOf("Penguin modern classics")))
        assertNull(ParseSeries.fromCatalogue(emptyList()))
    }

    @Test
    fun `the first numbered catalogue entry wins`() {
        val guess = ParseSeries.fromCatalogue(listOf("Ace science fiction", "Dune ; 2"))

        assertEquals(SeriesGuess("Dune", 2.0), guess)
    }

    @Test
    fun `a candidate takes its guess from the subtitle, then the title`() {
        val fromSubtitle = candidate(title = "Caliban's War", subtitle = "Book 2 of The Expanse")
            .withSeriesGuess()
        val fromTitle = candidate(title = "Leviathan Wakes (The Expanse, Book 1)").withSeriesGuess()

        assertEquals("The Expanse", fromSubtitle.series)
        assertEquals(2.0, fromSubtitle.seriesIndex)
        assertEquals("The Expanse", fromTitle.series)
        assertEquals(1.0, fromTitle.seriesIndex)
    }

    @Test
    fun `a candidate that already names a series keeps it and its text`() {
        val given =
            candidate(subtitle = "Book 2 of The Expanse", series = "Dune", seriesIndex = 1.0)

        assertEquals(given, given.withSeriesGuess())
    }

    @Test
    fun `a candidate with nothing to guess from stays as it is`() {
        val given = candidate(title = "1984")

        assertEquals(given, given.withSeriesGuess())
    }

    private fun candidate(
        title: String = "Title",
        subtitle: String? = null,
        series: String? = null,
        seriesIndex: Double? = null,
    ) = BookCandidate(
        title = title,
        subtitle = subtitle,
        series = series,
        seriesIndex = seriesIndex,
        source = BookSource.GOOGLE,
    )
}
