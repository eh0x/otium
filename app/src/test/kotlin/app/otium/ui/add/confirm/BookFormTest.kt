package app.otium.ui.add.confirm

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
    fun `choosing a new shelf adds it in front`() {
        val form = BookForm(categories = "Fiction").withShelf("Philosophy")

        assertEquals("Philosophy, Fiction", form.categories)
    }
}
