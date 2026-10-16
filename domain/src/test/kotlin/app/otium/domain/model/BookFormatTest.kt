package app.otium.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookFormatTest {

    @Test
    fun `goodreads bindings for paper are print`() {
        listOf("Paperback", "Hardcover", "Mass Market Paperback", "Library Binding", "Board Book")
            .forEach { assertEquals(it, BookFormat.PRINT, BookFormat.fromBinding(it)) }
    }

    @Test
    fun `goodreads bindings for screens are ebook`() {
        listOf("Kindle Edition", "ebook", "Nook", "Digital").forEach {
            assertEquals(it, BookFormat.EBOOK, BookFormat.fromBinding(it))
        }
    }

    @Test
    fun `goodreads bindings for sound are audiobook, not print`() {
        listOf("Audible Audio", "Audio CD", "Audiobook", "Audio Cassette").forEach {
            assertEquals(it, BookFormat.AUDIOBOOK, BookFormat.fromBinding(it))
        }
    }

    @Test
    fun `case and spaces do not matter`() {
        assertEquals(BookFormat.EBOOK, BookFormat.fromBinding("  KINDLE edition "))
    }

    @Test
    fun `a binding that names no format, or none, is unset`() {
        assertNull(BookFormat.fromBinding(""))
        assertNull(BookFormat.fromBinding(null))
        assertNull(BookFormat.fromBinding("Unknown Binding"))
        assertNull(BookFormat.fromBinding("Spiral-bound"))
    }

    @Test
    fun `the word otium writes is the word it reads back`() {
        BookFormat.entries.forEach { assertEquals(it, BookFormat.fromBinding(it.binding)) }
    }
}
