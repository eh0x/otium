package app.otium.ui.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesNumbersTest {

    @Test
    fun `whole numbers show without a fraction`() {
        assertEquals("2", formatSeriesNumber(2.0, Locale.US))
        assertEquals("0", formatSeriesNumber(0.0, Locale.US))
    }

    @Test
    fun `fractions use the locale's decimal sign`() {
        assertEquals("1.5", formatSeriesNumber(1.5, Locale.US))
        assertEquals("1,5", formatSeriesNumber(1.5, Locale.GERMANY))
    }

    @Test
    fun `typed numbers accept a point or a comma`() {
        assertEquals(1.5, parseSeriesNumber(" 1.5 "))
        assertEquals(1.5, parseSeriesNumber("1,5"))
        assertEquals(3.0, parseSeriesNumber("3"))
    }

    @Test
    fun `anything that is not a number of zero or more is no number`() {
        listOf("", "  ", "two", "-1", "NaN", "Infinity", "1.2.3").forEach {
            assertNull(it, parseSeriesNumber(it))
        }
    }
}
