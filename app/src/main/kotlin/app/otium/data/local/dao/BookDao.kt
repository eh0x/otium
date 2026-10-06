package app.otium.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import app.otium.data.local.entity.AuthorEntity
import app.otium.data.local.entity.BookAuthorCrossRef
import app.otium.data.local.entity.BookCategoryCrossRef
import app.otium.data.local.entity.BookEntity
import app.otium.data.local.entity.BookTagCrossRef
import app.otium.data.local.entity.CategoryEntity
import app.otium.data.local.entity.TagEntity
import app.otium.data.local.row.BookListRow
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookDao {

    // --- Library query (built by LibraryQueryBuilder) ---

    @RawQuery(
        observedEntities = [
            BookEntity::class,
            AuthorEntity::class, BookAuthorCrossRef::class,
            CategoryEntity::class, BookCategoryCrossRef::class,
            TagEntity::class, BookTagCrossRef::class,
        ],
    )
    abstract fun observeLibrary(query: SupportSQLiteQuery): Flow<List<BookListRow>>

    // --- Single book ---

    @Query("SELECT * FROM book WHERE id = :id")
    abstract fun observeBookEntity(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM book WHERE id = :id")
    abstract suspend fun getBookEntity(id: Long): BookEntity?

    @Query("SELECT * FROM book WHERE isbn13 = :isbn13")
    abstract suspend fun findEntityByIsbn13(isbn13: String): BookEntity?

    @Query(
        "SELECT a.name FROM author a JOIN book_author ba ON ba.authorId = a.id " +
            "WHERE ba.bookId = :bookId ORDER BY ba.position",
    )
    abstract fun observeAuthorNames(bookId: Long): Flow<List<String>>

    @Query(
        "SELECT c.name FROM category c JOIN book_category bc ON bc.categoryId = c.id " +
            "WHERE bc.bookId = :bookId ORDER BY bc.position",
    )
    abstract fun observeCategoryNames(bookId: Long): Flow<List<String>>

    @Query(
        "SELECT t.name FROM tag t JOIN book_tag bt ON bt.tagId = t.id " +
            "WHERE bt.bookId = :bookId ORDER BY bt.position",
    )
    abstract fun observeTagNames(bookId: Long): Flow<List<String>>

    @Query(
        "SELECT a.name FROM author a JOIN book_author ba ON ba.authorId = a.id " +
            "WHERE ba.bookId = :bookId ORDER BY ba.position",
    )
    abstract suspend fun authorNames(bookId: Long): List<String>

    @Query(
        "SELECT c.name FROM category c JOIN book_category bc ON bc.categoryId = c.id " +
            "WHERE bc.bookId = :bookId ORDER BY bc.position",
    )
    abstract suspend fun categoryNames(bookId: Long): List<String>

    @Query(
        "SELECT t.name FROM tag t JOIN book_tag bt ON bt.tagId = t.id " +
            "WHERE bt.bookId = :bookId ORDER BY bt.position",
    )
    abstract suspend fun tagNames(bookId: Long): List<String>

    // --- All labels (for filter pickers) ---

    @Query("SELECT name FROM author ORDER BY name COLLATE NOCASE")
    abstract fun observeAllAuthors(): Flow<List<String>>

    @Query("SELECT name FROM category ORDER BY name COLLATE NOCASE")
    abstract fun observeAllCategories(): Flow<List<String>>

    @Query("SELECT name FROM tag ORDER BY name COLLATE NOCASE")
    abstract fun observeAllTags(): Flow<List<String>>

    // --- Writes ---

    @Insert
    protected abstract suspend fun insertBookEntity(entity: BookEntity): Long

    @Update
    protected abstract suspend fun updateBookEntity(entity: BookEntity): Int

    @Query("DELETE FROM book WHERE id = :id")
    protected abstract suspend fun deleteBookEntity(id: Long): Int

    @Query("UPDATE book SET currentPage = :currentPage, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateProgress(id: Long, currentPage: Int, updatedAt: Long): Int

    @Transaction
    open suspend fun insertBook(
        entity: BookEntity,
        authors: List<String>,
        categories: List<String>,
        tags: List<String>,
    ): Long {
        val id = insertBookEntity(entity.copy(id = 0L))
        replaceLabels(id, authors, categories, tags)
        return id
    }

    /** Returns false when no book has the entity's id. */
    @Transaction
    open suspend fun updateBook(
        entity: BookEntity,
        authors: List<String>,
        categories: List<String>,
        tags: List<String>,
    ): Boolean {
        if (updateBookEntity(entity) == 0) return false
        replaceLabels(entity.id, authors, categories, tags)
        return true
    }

    /** Returns false when no book has the id. Labels left unused are removed. */
    @Transaction
    open suspend fun deleteBook(id: Long): Boolean {
        if (deleteBookEntity(id) == 0) return false
        deleteUnusedLabels()
        return true
    }

    // --- Label plumbing ---

    @Query("SELECT id FROM author WHERE name = :name")
    protected abstract suspend fun authorId(name: String): Long?

    @Query("SELECT id FROM category WHERE name = :name")
    protected abstract suspend fun categoryId(name: String): Long?

    @Query("SELECT id FROM tag WHERE name = :name")
    protected abstract suspend fun tagId(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAuthor(entity: AuthorEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCategory(entity: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTag(entity: TagEntity): Long

    @Insert
    protected abstract suspend fun insertAuthorRefs(refs: List<BookAuthorCrossRef>)

    @Insert
    protected abstract suspend fun insertCategoryRefs(refs: List<BookCategoryCrossRef>)

    @Insert
    protected abstract suspend fun insertTagRefs(refs: List<BookTagCrossRef>)

    @Query("DELETE FROM book_author WHERE bookId = :bookId")
    protected abstract suspend fun clearAuthorRefs(bookId: Long)

    @Query("DELETE FROM book_category WHERE bookId = :bookId")
    protected abstract suspend fun clearCategoryRefs(bookId: Long)

    @Query("DELETE FROM book_tag WHERE bookId = :bookId")
    protected abstract suspend fun clearTagRefs(bookId: Long)

    @Query("DELETE FROM author WHERE id NOT IN (SELECT authorId FROM book_author)")
    protected abstract suspend fun deleteUnusedAuthors()

    @Query("DELETE FROM category WHERE id NOT IN (SELECT categoryId FROM book_category)")
    protected abstract suspend fun deleteUnusedCategories()

    @Query("DELETE FROM tag WHERE id NOT IN (SELECT tagId FROM book_tag)")
    protected abstract suspend fun deleteUnusedTags()

    private suspend fun deleteUnusedLabels() {
        deleteUnusedAuthors()
        deleteUnusedCategories()
        deleteUnusedTags()
    }

    private suspend fun replaceLabels(
        bookId: Long,
        authors: List<String>,
        categories: List<String>,
        tags: List<String>,
    ) {
        clearAuthorRefs(bookId)
        clearCategoryRefs(bookId)
        clearTagRefs(bookId)

        insertAuthorRefs(
            cleanNames(authors).mapIndexed { index, name ->
                BookAuthorCrossRef(bookId, authorIdFor(name), index)
            },
        )
        insertCategoryRefs(
            cleanNames(categories).mapIndexed { index, name ->
                BookCategoryCrossRef(bookId, categoryIdFor(name), index)
            },
        )
        insertTagRefs(
            cleanNames(tags).mapIndexed { index, name ->
                BookTagCrossRef(bookId, tagIdFor(name), index)
            },
        )

        deleteUnusedLabels()
    }

    /** Trims, drops blanks and removes case-insensitive duplicates, keeping the first spelling. */
    private fun cleanNames(names: List<String>): List<String> =
        names.map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase() }

    private suspend fun authorIdFor(name: String): Long =
        authorId(name) ?: insertAuthor(AuthorEntity(name = name)).takeIf { it != -1L } ?: checkNotNull(authorId(name))

    private suspend fun categoryIdFor(name: String): Long =
        categoryId(name) ?: insertCategory(CategoryEntity(name = name)).takeIf { it != -1L } ?: checkNotNull(categoryId(name))

    private suspend fun tagIdFor(name: String): Long =
        tagId(name) ?: insertTag(TagEntity(name = name)).takeIf { it != -1L } ?: checkNotNull(tagId(name))
}
