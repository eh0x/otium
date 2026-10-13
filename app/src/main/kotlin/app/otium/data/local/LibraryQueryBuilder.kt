package app.otium.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.SortOrder

data class BuiltQuery(val sql: String, val args: List<Any>) {
    fun toSupportQuery(): SupportSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
}

/**
 * Builds the single library query from a [LibraryFilter]. All user text goes in as bound
 * arguments; only fixed SQL fragments are ever concatenated.
 *
 * The status clauses must stay in sync with the domain DeriveStatus rules (covered by a
 * boundary test in BookRepositoryTest).
 */
object LibraryQueryBuilder {

    private val SEARCH_TOKEN = Regex("[\\p{L}\\p{N}]+")

    /** A book's shelf is its first category. */
    private const val SHELF = "(SELECT c.name FROM book_category bc " +
        "JOIN category c ON c.id = bc.categoryId " +
        "WHERE bc.bookId = b.id ORDER BY bc.position LIMIT 1)"

    fun build(filter: LibraryFilter): BuiltQuery {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()

        ftsQuery(filter.query)?.let { match ->
            conditions += "b.id IN (SELECT rowid FROM book_fts WHERE book_fts MATCH ?)"
            args += match
        }

        filter.status?.let { conditions += statusCondition(it) }

        if (filter.categories.isNotEmpty()) {
            val names = filter.categories.sorted()
            conditions += "b.id IN (SELECT bc.bookId FROM book_category bc " +
                "JOIN category c ON c.id = bc.categoryId " +
                "WHERE c.name IN (${placeholders(names.size)}))"
            args.addAll(names)
        }

        if (filter.tags.isNotEmpty()) {
            val names = filter.tags.sorted()
            conditions += "b.id IN (SELECT bt.bookId FROM book_tag bt " +
                "JOIN tag t ON t.id = bt.tagId WHERE t.name IN (${placeholders(names.size)}))"
            args.addAll(names)
        }

        filter.author?.takeIf(String::isNotBlank)?.let { author ->
            conditions += "b.id IN (SELECT ba.bookId FROM book_author ba " +
                "JOIN author a ON a.id = ba.authorId WHERE a.name = ?)"
            args += author
        }

        filter.series?.let { series ->
            conditions += "b.series = ?"
            args += series
        }

        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        val sql = "SELECT b.id, b.title, b.authors, b.coverPath, b.coverSourceUrl, " +
            "b.currentPage, b.totalPages, $SHELF AS shelf, b.updatedAt, b.series, b.seriesIndex " +
            "FROM book b$where ORDER BY ${orderBy(filter.sort)}"
        return BuiltQuery(sql, args)
    }

    /**
     * Turns free text into an FTS4 prefix query ("the hobb" becomes "the* hobb*").
     * Only letters and digits are kept, so FTS syntax typed by the user is neutralized
     * (an operator word becomes an ordinary prefix term). Returns null when no
     * searchable text remains.
     */
    fun ftsQuery(text: String): String? {
        val tokens = SEARCH_TOKEN.findAll(text).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    private fun placeholders(count: Int) = List(count) { "?" }.joinToString(", ")

    private fun statusCondition(status: BookStatus): String = when (status) {
        BookStatus.NOT_STARTED -> "(b.currentPage <= 0 OR b.totalPages <= 0)"

        BookStatus.IN_PROGRESS ->
            "(b.currentPage > 0 AND b.totalPages > 0 AND b.currentPage < b.totalPages)"

        BookStatus.READ -> "(b.totalPages > 0 AND b.currentPage >= b.totalPages)"
    }

    /**
     * SQL order. The repository re-sorts title, author and series with the domain SortKeys, which
     * file titles and series without their leading article and authors by surname.
     */
    private fun orderBy(sort: SortOrder): String = when (sort) {
        SortOrder.TITLE -> "b.title COLLATE NOCASE ASC, b.id ASC"

        SortOrder.AUTHOR -> "b.authors COLLATE NOCASE ASC, b.title COLLATE NOCASE ASC, b.id ASC"

        SortOrder.SERIES ->
            "b.series IS NULL, b.series COLLATE NOCASE ASC, b.seriesIndex IS NULL, " +
                "b.seriesIndex ASC, b.title COLLATE NOCASE ASC, b.id ASC"

        SortOrder.RECENTLY_ADDED -> "b.addedAt DESC, b.id DESC"

        SortOrder.LAST_UPDATED -> "b.updatedAt DESC, b.id DESC"

        SortOrder.PROGRESS ->
            "(CASE WHEN b.totalPages > 0 " +
                "THEN MIN(1.0, b.currentPage * 1.0 / b.totalPages) ELSE 0 END) DESC, " +
                "b.title COLLATE NOCASE ASC, b.id ASC"
    }
}
