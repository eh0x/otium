package app.otium.domain.transfer

import app.otium.domain.fakes.book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture follows the Goodreads export columns as they are commonly documented; it has not
 * been checked against a fresh export, so replace it with a real (anonymised) one when possible.
 */
class GoodreadsCsvTest {

    private val table = CsvTable.of(fixture("goodreads_library_export.csv"))!!
    private val rows = GoodreadsCsv.read(table)

    @Test
    fun `a goodreads export is recognised and a storygraph one is not`() {
        assertTrue(GoodreadsCsv.matches(table))
        assertFalse(GoodreadsCsv.matches(CsvTable.of(fixture("storygraph_export.csv"))!!))
    }

    @Test
    fun `every row is read, including a review that spans lines`() {
        assertEquals(
            listOf("Pride and Prejudice", "The Hobbit", "Good Omens", "The Way of Kings"),
            rows.map { it.title },
        )
    }

    @Test
    fun `the details of a row are read`() {
        val pride = rows[0]

        assertEquals(listOf("Jane Austen"), pride.authors)
        assertEquals("9780141439518", pride.isbn)
        assertEquals("0141439513", pride.isbn10)
        assertEquals("Penguin Classics", pride.publisher)
        assertEquals(2002, pride.publishedYear)
        assertEquals(480, pride.totalPages)
        assertEquals(Shelf.READ, pride.shelf)
        assertEquals(listOf("classics", "favourites"), pride.tags)
        assertEquals(1_577_145_600_000L, pride.addedAt) // 2019-12-24 UTC
        assertFalse(pride.complete)
    }

    @Test
    fun `isbns are read whether or not the spreadsheet guard is quoted`() {
        assertEquals("9780547928227", rows[1].isbn)
        assertNull(rows[1].isbn10)
        assertNull(rows[2].isbn)
    }

    @Test
    fun `additional authors follow the main author`() {
        assertEquals(listOf("Terry Pratchett", "Neil Gaiman"), rows[2].authors)
    }

    @Test
    fun `the original year stands in for a missing edition year`() {
        assertEquals(1990, rows[2].publishedYear)
        assertEquals(0, rows[2].totalPages)
    }

    @Test
    fun `exclusive shelves become shelves and are not repeated as tags`() {
        assertEquals(Shelf.READING, rows[1].shelf)
        assertEquals(emptyList<String>(), rows[1].tags)
        assertEquals(Shelf.TO_READ, rows[2].shelf)
        assertEquals(listOf("fantasy"), rows[2].tags)
    }

    @Test
    fun `a custom exclusive shelf is kept as a tag`() {
        assertEquals(Shelf.TO_READ, rows[3].shelf)
        assertEquals(listOf("did-not-finish"), rows[3].tags)
    }

    @Test
    fun `an otium export reads back with its books, progress and labels`() {
        val books = listOf(
            book(
                isbn13 = "9780306406157",
                title = "Finished, \"quoted\"",
                totalPages = 200,
                currentPage = 200,
                categories = listOf("Science Fiction"),
                tags = listOf("signed"),
            ),
            book(title = "Unread", totalPages = 0),
        )

        val table = CsvTable.of(GoodreadsCsv.write(books))!!
        val back = GoodreadsCsv.read(table)

        assertTrue(GoodreadsCsv.matches(table))
        assertEquals(listOf("Finished, \"quoted\"", "Unread"), back.map { it.title })
        assertEquals("9780306406157", back[0].isbn)
        assertEquals(Shelf.READ, back[0].shelf)
        assertEquals(listOf("Science-Fiction", "signed"), back[0].tags)
        assertEquals(200, back[0].totalPages)
        assertEquals(listOf("Some Author"), back[0].authors)
        assertNull(back[1].isbn)
        assertEquals(Shelf.TO_READ, back[1].shelf)
    }

    @Test
    fun `text a spreadsheet would run as a formula is written as text`() {
        val hostile = book(isbn13 = "9780306406157", title = "=HYPERLINK(\"http://x\")")
            .copy(
                authors = listOf("+Plus", "@At"),
                publisher = "-Minus",
                tags = listOf("\tTabbed"),
            )

        val record = CsvTable.of(GoodreadsCsv.write(listOf(hostile)))!!.records.single()

        assertEquals("'=HYPERLINK(\"http://x\")", record["Title"])
        assertEquals("'+Plus", record["Author"])
        assertEquals("'@At", record["Additional Authors"])
        assertEquals("'-Minus", record["Publisher"])
        assertEquals("the isbn guard stays a formula", "=\"9780306406157\"", record["ISBN13"])
    }

    @Test
    fun `escaped text reads back as it was and plain text is untouched`() {
        val books = listOf(
            book(title = "=SUM(A1:A9)").copy(authors = listOf("-Dash"), publisher = "@Press"),
            book(title = "It's fine").copy(publisher = "O'Reilly"),
        )

        val back = GoodreadsCsv.read(CsvTable.of(GoodreadsCsv.write(books))!!)

        assertEquals("=SUM(A1:A9)", back[0].title)
        assertEquals(listOf("-Dash"), back[0].authors)
        assertEquals("@Press", back[0].publisher)
        assertEquals("It's fine", back[1].title)
        assertEquals("O'Reilly", back[1].publisher)
    }

    @Test
    fun `the export writes every goodreads column with the isbn guard`() {
        val text = GoodreadsCsv.write(listOf(book(isbn13 = "9780306406157")))
        val record = CsvTable.of(text)!!.records.single()

        assertEquals(GoodreadsCsv.COLUMNS, Csv.read(text).first())
        assertEquals("=\"9780306406157\"", record["ISBN13"])
        assertEquals("1970/01/01", record["Date Added"])
        assertEquals("Author, Some", record["Author l-f"])
    }
}

internal fun fixture(name: String): String =
    requireNotNull(GoodreadsCsvTest::class.java.getResource("/transfer/$name")) { name }.readText()
