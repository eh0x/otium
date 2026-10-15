package app.otium.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SortKeysTest {

    private fun book(
        id: Long,
        title: String,
        authors: String = "",
        series: String? = null,
        seriesIndex: Double? = null,
    ) = BookSummary(
        id = id,
        title = title,
        authors = authors,
        series = series,
        seriesIndex = seriesIndex,
        coverPath = null,
        coverSourceUrl = null,
        currentPage = 0,
        totalPages = 0,
        status = BookStatus.NOT_STARTED,
    )

    @Test
    fun `titles file under their first significant word`() {
        assertEquals("Hobbit", SortKeys.title("The Hobbit"))
        assertEquals("Game of Thrones", SortKeys.title("A Game of Thrones"))
        assertEquals(
            "Essay Concerning Human Understanding",
            SortKeys.title("An Essay Concerning Human Understanding"),
        )
        assertEquals("Theory of Justice", SortKeys.title("Theory of Justice"))
        assertEquals("Dune", SortKeys.title("  Dune "))
    }

    @Test
    fun `a title that is only an article keeps it`() {
        assertEquals("The", SortKeys.title("The"))
    }

    @Test
    fun `the surname is the last word of the first author`() {
        assertEquals("Aurelius", SortKeys.surname("Marcus Aurelius"))
        assertEquals("Pratchett", SortKeys.surname("Terry Pratchett, Neil Gaiman"))
        assertEquals("Homer", SortKeys.surname("Homer"))
        assertEquals("", SortKeys.surname(""))
    }

    @Test
    fun `particles stay with the surname`() {
        assertEquals("Le Guin", SortKeys.surname("Ursula K. Le Guin"))
        assertEquals("van Gogh", SortKeys.surname("Vincent van Gogh"))
        assertEquals("de la Mare", SortKeys.surname("Walter de la Mare"))
        assertEquals("da Vinci", SortKeys.surname("Leonardo da Vinci"))
    }

    @Test
    fun `a first name that looks like a particle is not taken for one`() {
        assertEquals("Fontaine", SortKeys.surname("La Fontaine"))
        assertEquals("Rosa", SortKeys.surname("Del Rosa"))
    }

    @Test
    fun `by title ignores articles and case`() {
        val books =
            listOf(book(1, "The Odyssey"), book(2, "beowulf"), book(3, "A Clockwork Orange"))

        assertEquals(
            listOf("beowulf", "A Clockwork Orange", "The Odyssey"),
            books.sortedWith(SortKeys.byTitle).map { it.title },
        )
    }

    @Test
    fun `by author orders by surname, then title`() {
        val books = listOf(
            book(1, "Emma", "Jane Austen"),
            book(2, "Dune", "Frank Herbert"),
            book(3, "Persuasion", "Jane Austen"),
            book(4, "Meditations", "Marcus Aurelius"),
        )

        assertEquals(
            listOf("Meditations", "Emma", "Persuasion", "Dune"),
            books.sortedWith(SortKeys.byAuthor).map { it.title },
        )
    }

    @Test
    fun `equal keys fall back to the id so the order is stable`() {
        val books = listOf(book(2, "Dune"), book(1, "dune"))

        assertEquals(listOf(1L, 2L), books.sortedWith(SortKeys.byTitle).map { it.id })
    }

    @Test
    fun `by author keeps an author's series in reading order`() {
        val books = listOf(
            book(1, "Abaddon's Gate", "James S. A. Corey", "The Expanse", 3.0),
            book(2, "Leviathan Wakes", "James S. A. Corey", "The Expanse", 1.0),
            book(3, "Caliban's War", "James S. A. Corey", "The Expanse", 2.0),
        )

        assertEquals(
            listOf("Leviathan Wakes", "Caliban's War", "Abaddon's Gate"),
            books.sortedWith(SortKeys.byAuthor).map { it.title },
        )
    }

    @Test
    fun `by series groups each series in number order, books outside a series last`() {
        val books = listOf(
            book(1, "Emma"),
            book(2, "Children of Dune", series = "Dune", seriesIndex = 3.0),
            book(3, "Caliban's War", series = "The Expanse", seriesIndex = 2.0),
            book(4, "Dune", series = "Dune", seriesIndex = 1.0),
            book(5, "Dune Messiah", series = "Dune", seriesIndex = 2.0),
            book(6, "Leviathan Wakes", series = "The Expanse", seriesIndex = 1.0),
            book(7, "Unnumbered", series = "Dune"),
            book(8, "Interlude", series = "Dune", seriesIndex = 1.5),
        )

        assertEquals(
            listOf(
                "Dune",
                "Interlude",
                "Dune Messiah",
                "Children of Dune",
                "Unnumbered",
                "Leviathan Wakes",
                "Caliban's War",
                "Emma",
            ),
            books.sortedWith(SortKeys.bySeries).map { it.title },
        )
    }

    @Test
    fun `series names file like titles`() {
        assertEquals("Expanse", SortKeys.series("The Expanse"))
        assertEquals("Discworld", SortKeys.series(" Discworld "))
    }
}
