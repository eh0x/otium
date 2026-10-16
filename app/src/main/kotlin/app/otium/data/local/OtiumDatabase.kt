package app.otium.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import app.otium.data.local.dao.BookDao
import app.otium.data.local.dao.LibraryTransferDao
import app.otium.data.local.dao.LoanDao
import app.otium.data.local.dao.NoteDao
import app.otium.data.local.entity.AuthorEntity
import app.otium.data.local.entity.BookAuthorCrossRef
import app.otium.data.local.entity.BookCategoryCrossRef
import app.otium.data.local.entity.BookEntity
import app.otium.data.local.entity.BookFtsEntity
import app.otium.data.local.entity.BookTagCrossRef
import app.otium.data.local.entity.CategoryEntity
import app.otium.data.local.entity.LoanEntity
import app.otium.data.local.entity.NoteEntity
import app.otium.data.local.entity.TagEntity

@Database(
    entities = [
        BookEntity::class,
        AuthorEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        BookAuthorCrossRef::class,
        BookCategoryCrossRef::class,
        BookTagCrossRef::class,
        BookFtsEntity::class,
        NoteEntity::class,
        LoanEntity::class,
    ],
    version = OtiumDatabase.VERSION,
    exportSchema = true,
)
abstract class OtiumDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    abstract fun transferDao(): LibraryTransferDao

    abstract fun noteDao(): NoteDao

    abstract fun loanDao(): LoanDao

    companion object {
        /** Bump with every schema change and add the matching entry to [migrations]. */
        const val VERSION = 4

        val migrations: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
