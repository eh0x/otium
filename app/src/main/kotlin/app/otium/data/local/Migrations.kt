package app.otium.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val FTS_TRIGGER = "room_fts_content_sync_book_fts"
private const val FTS_COLUMNS = "`title`, `subtitle`, `authors`, `series`"
private const val FTS_VALUES = "NEW.`rowid`, NEW.`title`, NEW.`subtitle`, NEW.`authors`, " +
    "NEW.`series`"

/**
 * Version 2 adds a book's series and its number in it, and indexes the series for search. An
 * FTS4 table cannot gain a column, so the index and its sync triggers are made again exactly as
 * Room makes them (see the exported schema 2.json) and the index is rebuilt from the book table.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `book` ADD COLUMN `series` TEXT")
        db.execSQL("ALTER TABLE `book` ADD COLUMN `seriesIndex` REAL")

        listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT").forEach {
            db.execSQL("DROP TRIGGER IF EXISTS ${FTS_TRIGGER}_$it")
        }
        db.execSQL("DROP TABLE IF EXISTS `book_fts`")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `book_fts` USING FTS4(`title` TEXT NOT NULL, " +
                "`subtitle` TEXT, `authors` TEXT NOT NULL, `series` TEXT, tokenize=unicode61, " +
                "content=`book`)",
        )
        for (event in listOf("UPDATE", "DELETE")) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS ${FTS_TRIGGER}_BEFORE_$event BEFORE $event ON " +
                    "`book` BEGIN DELETE FROM `book_fts` WHERE `docid`=OLD.`rowid`; END",
            )
        }
        for (event in listOf("UPDATE", "INSERT")) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS ${FTS_TRIGGER}_AFTER_$event AFTER $event ON " +
                    "`book` BEGIN INSERT INTO `book_fts`(`docid`, $FTS_COLUMNS) " +
                    "VALUES ($FTS_VALUES); END",
            )
        }
        db.execSQL("INSERT INTO `book_fts`(`book_fts`) VALUES('rebuild')")
    }
}

/**
 * Version 3 adds notes and quotes (Marginalia): one table, deleted with its book. Nothing else
 * changes, so the book index is left alone. The SQL must match Room's character for character
 * (see the exported schema 3.json, down to the space before the last bracket): a restore refuses
 * a backup whose schema text differs from a fresh library's.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bookId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`page` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`bookId`) REFERENCES `book`(`id`) ON UPDATE NO ACTION " +
                "ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_bookId` ON `note` (`bookId`)")
    }
}

/**
 * Version 4 adds the lending ledger: one table, deleted with its book. As for version 3, the SQL
 * must match Room's character for character (see the exported schema 4.json).
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `loan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`bookId` INTEGER NOT NULL, `counterparty` TEXT NOT NULL, " +
                "`direction` TEXT NOT NULL, `lentOn` INTEGER NOT NULL, `remindOn` INTEGER, " +
                "`returnedOn` INTEGER, FOREIGN KEY(`bookId`) REFERENCES `book`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_bookId` ON `loan` (`bookId`)")
    }
}
