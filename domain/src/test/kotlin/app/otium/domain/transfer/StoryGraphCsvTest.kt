package app.otium.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture follows the StoryGraph export columns as they are commonly documented; it has not
 * been checked against a fresh export, so replace it with a real (anonymised) one when possible.
 */
class StoryGraphCsvTest {

    private val table = CsvTable.of(fixture("storygraph_export.csv"))!!
    private val rows = StoryGraphCsv.read(table)

    @Test
    fun `a storygraph export is recognised and a goodreads one is not`() {
        assertTrue(StoryGraphCsv.matches(table))
        assertFalse(StoryGraphCsv.matches(CsvTable.of(fixture("goodreads_library_export.csv"))!!))
    }

    @Test
    fun `the details of a row are read`() {
        val pride = rows[0]

        assertEquals("Pride and Prejudice", pride.title)
        assertEquals(listOf("Jane Austen"), pride.authors)
        assertEquals("9780141439518", pride.isbn)
        assertEquals(Shelf.READ, pride.shelf)
        assertEquals(listOf("classics", "romance"), pride.tags)
        assertEquals(1_680_393_600_000L, pride.addedAt) // 2023-04-02 UTC
        assertEquals(0, pride.totalPages)
    }

    @Test
    fun `several authors are split`() {
        assertEquals(listOf("Terry Pratchett", "Neil Gaiman"), rows[1].authors)
        assertEquals(Shelf.TO_READ, rows[1].shelf)
    }

    @Test
    fun `a status otium has no shelf for is kept as a tag`() {
        assertEquals(Shelf.TO_READ, rows[2].shelf)
        assertEquals(listOf("did-not-finish"), rows[2].tags)
    }

    @Test
    fun `a book being read without an isbn is read too`() {
        assertEquals("The Hobbit", rows[3].title)
        assertEquals(Shelf.READING, rows[3].shelf)
        assertNull(rows[3].isbn)
    }
}
