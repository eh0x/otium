package app.otium.data.local

import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryQueryBuilderTest {

    @Test
    fun `default filter selects everything ordered by title`() {
        val query = LibraryQueryBuilder.build(LibraryFilter())

        assertTrue(query.sql.contains("FROM book b"))
        assertFalse(query.sql.contains("MATCH"))
        assertTrue(query.sql.contains("ORDER BY b.title COLLATE NOCASE ASC"))
        assertTrue(query.args.isEmpty())
    }

    @Test
    fun `ftsQuery turns words into prefix terms`() {
        assertEquals("the* hobbit*", LibraryQueryBuilder.ftsQuery("the hobbit"))
        assertEquals("hobb*", LibraryQueryBuilder.ftsQuery("  hobb  "))
    }

    @Test
    fun `ftsQuery ignores how the words are spaced, so search text needs no cleaning first`() {
        assertEquals("the* hobbit*", LibraryQueryBuilder.ftsQuery("  the   hobbit \n"))
        assertEquals("the* hobbit*", LibraryQueryBuilder.ftsQuery("the\thobbit"))
        assertEquals(null, LibraryQueryBuilder.ftsQuery(" \n\t "))
    }

    @Test
    fun `ftsQuery strips fts syntax and punctuation`() {
        assertEquals("c*", LibraryQueryBuilder.ftsQuery("c++ !!"))
        assertEquals("o* brien*", LibraryQueryBuilder.ftsQuery("o'brien"))
        assertEquals("a* b*", LibraryQueryBuilder.ftsQuery("\"a\" b*"))
    }

    @Test
    fun `ftsQuery neutralizes fts operators by turning them into prefix terms`() {
        assertEquals("a* OR* b*", LibraryQueryBuilder.ftsQuery("a OR b"))
        assertEquals("a* NOT* b*", LibraryQueryBuilder.ftsQuery("a NOT b"))
        assertEquals("a* NEAR* b*", LibraryQueryBuilder.ftsQuery("a NEAR b"))
    }

    @Test
    fun `ftsQuery keeps letters from any script`() {
        assertEquals("garcía*", LibraryQueryBuilder.ftsQuery("garcía"))
        assertEquals("東京*", LibraryQueryBuilder.ftsQuery("東京"))
    }

    @Test
    fun `ftsQuery is null when nothing searchable remains`() {
        assertNull(LibraryQueryBuilder.ftsQuery(""))
        assertNull(LibraryQueryBuilder.ftsQuery("   "))
        assertNull(LibraryQueryBuilder.ftsQuery("!!! ---"))
    }

    @Test
    fun `text query adds a bound match clause`() {
        val query = LibraryQueryBuilder.build(LibraryFilter(query = "hobb"))

        assertTrue(query.sql.contains("book_fts MATCH ?"))
        assertEquals(listOf<Any>("hobb*"), query.args)
    }

    @Test
    fun `unsearchable text is ignored instead of matching nothing`() {
        val query = LibraryQueryBuilder.build(LibraryFilter(query = "!!!"))

        assertFalse(query.sql.contains("MATCH"))
        assertTrue(query.args.isEmpty())
    }

    @Test
    fun `status clauses match the derived status rules`() {
        val notStarted = LibraryQueryBuilder.build(
            LibraryFilter(status = BookStatus.NOT_STARTED),
        ).sql
        val inProgress = LibraryQueryBuilder.build(
            LibraryFilter(status = BookStatus.IN_PROGRESS),
        ).sql
        val read = LibraryQueryBuilder.build(LibraryFilter(status = BookStatus.READ)).sql

        assertTrue(notStarted.contains("(b.currentPage <= 0 OR b.totalPages <= 0)"))
        assertTrue(
            inProgress.contains(
                "(b.currentPage > 0 AND b.totalPages > 0 AND b.currentPage < b.totalPages)",
            ),
        )
        assertTrue(read.contains("(b.totalPages > 0 AND b.currentPage >= b.totalPages)"))
    }

    @Test
    fun `categories and tags are bound in sorted order`() {
        val query = LibraryQueryBuilder.build(
            LibraryFilter(categories = setOf("Poetry", "Fiction"), tags = setOf("zeta", "alpha")),
        )

        assertEquals(listOf<Any>("Fiction", "Poetry", "alpha", "zeta"), query.args)
        assertEquals(4, query.sql.count { it == '?' })
    }

    @Test
    fun `author filter is bound`() {
        val query = LibraryQueryBuilder.build(LibraryFilter(author = "Tolkien"))

        assertEquals(listOf<Any>("Tolkien"), query.args)
    }

    @Test
    fun `user text never appears in the sql string`() {
        val hostile = "'; DROP TABLE book;--"
        val query = LibraryQueryBuilder.build(
            LibraryFilter(
                query = hostile,
                categories = setOf(hostile),
                tags = setOf(hostile),
                author = hostile,
            ),
        )

        assertFalse(query.sql.contains("DROP"))
        assertFalse(query.sql.contains(hostile))
    }

    @Test
    fun `every sort order has its own ordering`() {
        fun orderBy(sort: SortOrder) =
            LibraryQueryBuilder.build(LibraryFilter(sort = sort)).sql.substringAfter("ORDER BY")

        assertTrue(orderBy(SortOrder.TITLE).contains("b.title COLLATE NOCASE ASC"))
        assertTrue(orderBy(SortOrder.AUTHOR).contains("b.authors COLLATE NOCASE ASC"))
        assertTrue(orderBy(SortOrder.RECENTLY_ADDED).contains("b.addedAt DESC"))
        assertTrue(orderBy(SortOrder.LAST_UPDATED).contains("b.updatedAt DESC"))
        assertTrue(orderBy(SortOrder.PROGRESS).contains("DESC"))
        assertTrue(orderBy(SortOrder.PROGRESS).contains("b.totalPages"))
    }
}
