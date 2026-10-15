package app.otium.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import app.otium.data.local.OtiumDatabase
import app.otium.data.local.dao.LibraryTransferDao
import app.otium.data.local.entity.AuthorEntity
import app.otium.data.local.entity.BookAuthorCrossRef
import app.otium.data.local.entity.BookCategoryCrossRef
import app.otium.data.local.entity.BookEntity
import app.otium.data.local.entity.BookTagCrossRef
import app.otium.data.local.entity.CategoryEntity
import app.otium.data.local.entity.NoteEntity
import app.otium.data.local.entity.TagEntity
import java.io.File

/** Every row of a library, read in one go. */
internal data class LibraryRows(
    val books: List<BookEntity>,
    val authors: List<AuthorEntity>,
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val bookAuthors: List<BookAuthorCrossRef>,
    val bookCategories: List<BookCategoryCrossRef>,
    val bookTags: List<BookTagCrossRef>,
    val notes: List<NoteEntity>,
)

/** Reads the whole library from one consistent view, whatever else is writing meanwhile. */
internal suspend fun OtiumDatabase.readAll(): LibraryRows = withTransaction {
    val dao = transferDao()
    LibraryRows(
        books = dao.books(),
        authors = dao.authors(),
        categories = dao.categories(),
        tags = dao.tags(),
        bookAuthors = dao.bookAuthors(),
        bookCategories = dao.bookCategories(),
        bookTags = dao.bookTags(),
        notes = dao.notes(),
    )
}

/**
 * Makes this database hold exactly [rows], in one transaction: when anything fails, it holds what
 * it held before. Foreign keys are on, so labels go in before the books that use them.
 */
internal suspend fun OtiumDatabase.replaceAll(rows: LibraryRows) = withTransaction {
    val dao: LibraryTransferDao = transferDao()
    dao.deleteBooks()
    dao.deleteAuthors()
    dao.deleteCategories()
    dao.deleteTags()
    dao.insertAuthors(rows.authors)
    dao.insertCategories(rows.categories)
    dao.insertTags(rows.tags)
    dao.insertBooks(rows.books)
    dao.insertBookAuthors(rows.bookAuthors)
    dao.insertBookCategories(rows.bookCategories)
    dao.insertBookTags(rows.bookTags)
    dao.insertNotes(rows.notes)
}

/**
 * Opens the database file at [file] as a stand-alone Room database, creating it when it does not
 * exist. It uses a rollback journal, so the file stands alone and needs no `-wal` or `-shm`
 * beside it. Room brings an older schema up to date and refuses a newer one.
 */
@Suppress("SpreadOperator") // Room only offers varargs; the array is tiny and spread once.
internal fun openStandalone(context: Context, file: File): OtiumDatabase =
    Room.databaseBuilder(context, OtiumDatabase::class.java, file.absolutePath)
        .addMigrations(*OtiumDatabase.migrations)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .build()
