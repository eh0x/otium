package app.otium.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration scaffolding. When the schema version is raised, add the Migration to
 * [OtiumDatabase.migrations] and a test that opens the previous exported schema and migrates it.
 */
@RunWith(RobolectricTestRunner::class)
class SchemaMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OtiumDatabase::class.java,
    )

    private val schemaDir = File("schemas/${OtiumDatabase::class.java.name}")

    @Test
    fun `exported schema exists for every version`() {
        for (version in 1..OtiumDatabase.VERSION) {
            assertTrue("missing schema $version.json", File(schemaDir, "$version.json").exists())
        }
    }

    @Test
    fun `current version matches the latest exported schema`() {
        val latest = schemaDir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .maxOf { it.nameWithoutExtension.toInt() }

        assertEquals(OtiumDatabase.VERSION, latest)
    }

    @Test
    fun `database opens from the exported version 1 schema`() {
        helper.createDatabase("migration-test", 1).close()

        helper.runMigrationsAndValidate(
            "migration-test",
            OtiumDatabase.VERSION,
            true,
            *OtiumDatabase.migrations,
        )
            .close()
    }

    @Test
    fun `version 1 books survive the move to series, without one and still searchable`() {
        helper.createDatabase("series-migration", 1).use { db ->
            db.execSQL(
                "INSERT INTO book (id, isbn13, isbn10, title, subtitle, authors, publisher, " +
                    "publishedYear, language, description, totalPages, currentPage, coverPath, " +
                    "coverSourceUrl, source, addedAt, updatedAt, userEdited, needsLookup) " +
                    "VALUES (7, '9780261103344', NULL, 'The Hobbit', 'There and Back Again', " +
                    "'J. R. R. Tolkien', NULL, 1937, 'en', NULL, 310, 12, NULL, NULL, " +
                    "'MANUAL', 1, 2, 0, 0)",
            )
        }

        helper.runMigrationsAndValidate("series-migration", 2, true, *OtiumDatabase.migrations)
            .use { db ->
                db.query("SELECT title, series, seriesIndex, currentPage FROM book").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("The Hobbit", it.getString(0))
                    assertTrue(it.isNull(1))
                    assertTrue(it.isNull(2))
                    assertEquals(12, it.getInt(3))
                }
                db.query("SELECT rowid FROM book_fts WHERE book_fts MATCH 'back*'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(7L, it.getLong(0))
                }
                db.execSQL("UPDATE book SET series = 'Middle-earth' WHERE id = 7")
                db.query("SELECT rowid FROM book_fts WHERE book_fts MATCH 'middle*'").use {
                    assertTrue(it.moveToFirst())
                }
            }
    }

    @Test
    fun `version 2 books survive the move to notes, and a book's notes go with it`() {
        helper.createDatabase("notes-migration", 2).use { db ->
            db.execSQL(
                "INSERT INTO book (id, isbn13, isbn10, title, subtitle, authors, publisher, " +
                    "publishedYear, language, description, totalPages, currentPage, coverPath, " +
                    "coverSourceUrl, source, addedAt, updatedAt, userEdited, needsLookup, " +
                    "series, seriesIndex) VALUES (7, '9780261103344', NULL, 'The Hobbit', NULL, " +
                    "'J. R. R. Tolkien', NULL, 1937, 'en', NULL, 310, 12, NULL, NULL, " +
                    "'MANUAL', 1, 2, 0, 0, 'Middle-earth', 1.0)",
            )
        }

        helper.runMigrationsAndValidate("notes-migration", 3, true, *OtiumDatabase.migrations)
            .use { db ->
                db.query("SELECT title, series, currentPage FROM book").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("The Hobbit", it.getString(0))
                    assertEquals("Middle-earth", it.getString(1))
                    assertEquals(12, it.getInt(2))
                }
                db.query("SELECT COUNT(*) FROM note").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }

                db.execSQL("PRAGMA foreign_keys = ON")
                db.execSQL(
                    "INSERT INTO note (bookId, kind, text, page, createdAt, updatedAt) " +
                        "VALUES (7, 'QUOTE', 'In a hole in the ground', 1, 3, 3)",
                )
                db.execSQL("DELETE FROM book WHERE id = 7")
                db.query("SELECT COUNT(*) FROM note").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
            }
    }
}
