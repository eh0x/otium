package app.otium.ui.add.confirm

import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookFormTest {

    @Test
    fun `the shelf is the first category`() {
        assertEquals("Fiction", BookForm(categories = "Fiction, Classics").shelf)
        assertNull(BookForm(categories = " ").shelf)
    }

    @Test
    fun `choosing a shelf moves it to the front and keeps the others`() {
        val form = BookForm(categories = "Fiction, Classics").withShelf("classics")

        assertEquals("classics, Fiction", form.categories)
    }

    @Test
    fun `a series and its number round trip through the form`() {
        val candidate = BookCandidate(
            title = "Dune",
            series = "Dune Chronicles",
            seriesIndex = 1.5,
            source = BookSource.GOOGLE,
        )

        val form = BookForm.from(candidate)
        val back = form.toCandidate(BookSource.GOOGLE, isbn10 = null)

        assertEquals("Dune Chronicles", form.series)
        assertEquals("Dune Chronicles", back.series)
        assertEquals(1.5, back.seriesIndex)
    }

    @Test
    fun `a blank series and a number that is not one are saved as none`() {
        val back = BookForm(title = "Dune", series = "  ", seriesIndex = "two")
            .toCandidate(BookSource.MANUAL, isbn10 = null)

        assertNull(back.series)
        assertNull(back.seriesIndex)
    }

    @Test
    fun `a typed number may use a comma`() {
        val back = BookForm(title = "Dune", series = "Dune", seriesIndex = "2,5")
            .toCandidate(BookSource.MANUAL, isbn10 = null)

        assertEquals(2.5, back.seriesIndex)
    }

    @Test
    fun `choosing a new shelf adds it in front`() {
        val form = BookForm(categories = "Fiction").withShelf("Philosophy")

        assertEquals("Philosophy, Fiction", form.categories)
    }
}
