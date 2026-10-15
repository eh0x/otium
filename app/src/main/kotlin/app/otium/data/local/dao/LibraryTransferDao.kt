package app.otium.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.otium.data.local.entity.AuthorEntity
import app.otium.data.local.entity.BookAuthorCrossRef
import app.otium.data.local.entity.BookCategoryCrossRef
import app.otium.data.local.entity.BookEntity
import app.otium.data.local.entity.BookTagCrossRef
import app.otium.data.local.entity.CategoryEntity
import app.otium.data.local.entity.NoteEntity
import app.otium.data.local.entity.TagEntity

/**
 * Whole-table reads and writes for moving a library between databases: the backup copies the live
 * one into a fresh file, the restore copies a backup into the live one. Ids are kept as they are,
 * so the join tables need no remapping. Callers wrap a copy in a transaction.
 */
@Dao
interface LibraryTransferDao {

    @Query("SELECT * FROM book ORDER BY id")
    suspend fun books(): List<BookEntity>

    @Query("SELECT * FROM author ORDER BY id")
    suspend fun authors(): List<AuthorEntity>

    @Query("SELECT * FROM category ORDER BY id")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM tag ORDER BY id")
    suspend fun tags(): List<TagEntity>

    @Query("SELECT * FROM book_author")
    suspend fun bookAuthors(): List<BookAuthorCrossRef>

    @Query("SELECT * FROM book_category")
    suspend fun bookCategories(): List<BookCategoryCrossRef>

    @Query("SELECT * FROM book_tag")
    suspend fun bookTags(): List<BookTagCrossRef>

    @Query("SELECT * FROM note ORDER BY id")
    suspend fun notes(): List<NoteEntity>

    /**
     * Deleting books removes their join rows and notes too (cascade) and the full-text index
     * (triggers).
     */
    @Query("DELETE FROM book")
    suspend fun deleteBooks()

    @Query("DELETE FROM author")
    suspend fun deleteAuthors()

    @Query("DELETE FROM category")
    suspend fun deleteCategories()

    @Query("DELETE FROM tag")
    suspend fun deleteTags()

    @Insert
    suspend fun insertAuthors(rows: List<AuthorEntity>)

    @Insert
    suspend fun insertCategories(rows: List<CategoryEntity>)

    @Insert
    suspend fun insertTags(rows: List<TagEntity>)

    @Insert
    suspend fun insertBooks(rows: List<BookEntity>)

    @Insert
    suspend fun insertBookAuthors(rows: List<BookAuthorCrossRef>)

    @Insert
    suspend fun insertBookCategories(rows: List<BookCategoryCrossRef>)

    @Insert
    suspend fun insertBookTags(rows: List<BookTagCrossRef>)

    @Insert
    suspend fun insertNotes(rows: List<NoteEntity>)
}
