package app.otium.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.otium.data.backup.archive.BackupManifest
import app.otium.data.backup.archive.BackupZip
import app.otium.data.cover.CoverFiles
import app.otium.data.local.OtiumDatabase
import app.otium.data.local.testBook
import app.otium.data.repo.RoomBookRepository
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.LibraryFilter
import app.otium.domain.port.DispatcherProvider
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Backs a library up with the real archiver and restores it with the real restorer. */
@RunWith(RobolectricTestRunner::class)
class LibraryBackupRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    private object RealDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val opened = mutableListOf<OtiumDatabase>()

    private lateinit var sourceCovers: File
    private lateinit var targetCovers: File
    private lateinit var source: OtiumDatabase
    private lateinit var target: OtiumDatabase
    private lateinit var sourceBooks: RoomBookRepository
    private lateinit var targetBooks: RoomBookRepository

    @Before
    fun setUp() {
        sourceCovers = temp.newFolder("source-covers")
        targetCovers = temp.newFolder("target-covers")
        source = database()
        target = database()
        sourceBooks = RoomBookRepository(source.bookDao(), CoverFiles(sourceCovers))
        targetBooks = RoomBookRepository(target.bookDao(), CoverFiles(targetCovers))
    }

    @After
    fun tearDown() = opened.forEach(OtiumDatabase::close)

    private fun database(): OtiumDatabase = Room.inMemoryDatabaseBuilder(
        context,
        OtiumDatabase::class.java,
    ).allowMainThreadQueries().build().also(opened::add)

    private fun archiver() = ZipBackupArchiver(
        context = context,
        database = source,
        files = BackupFiles(File(temp.root, "work"), sourceCovers),
        clock = { NOW },
        appVersion = "0.1.0",
        dispatchers = RealDispatchers,
    )

    private fun restorer() = RoomLibraryRestorer(
        context = context,
        database = target,
        files = BackupFiles(File(temp.root, "work"), targetCovers),
        dispatchers = RealDispatchers,
    )

    private fun cover(directory: File, name: String, content: String): File =
        File(directory, name).apply { writeText(content) }

    private suspend fun titles(books: RoomBookRepository): List<String> =
        books.observeLibrary(LibraryFilter()).first().map { it.title }

    /** Two books: one with labels, progress and a cover, one plain. Returns the first one's id. */
    private suspend fun fillSource(): Long {
        val hobbitCover = cover(sourceCovers, "hobbit.jpg", "hobbit picture")
        val hobbit = sourceBooks.add(
            testBook(
                title = "The Hobbit",
                authors = listOf("J. R. R. Tolkien", "Second Author"),
                categories = listOf("Fantasy"),
                tags = listOf("favourite"),
                isbn13 = "9780306406157",
                currentPage = 12,
            ).copy(coverPath = hobbitCover.path),
        )
        sourceBooks.add(testBook(title = "Dune", authors = listOf("Frank Herbert")))
        return (hobbit as Try.Success).value
    }

    private fun backupOfSource() = (runBlocking { archiver().create() } as Try.Success).value

    private suspend fun addKeeper() {
        targetBooks.add(
            testBook(title = "Keep Me").copy(
                coverPath = cover(targetCovers, "keep.jpg", "keep picture").path,
            ),
        )
    }

    /** The database file of a real backup, for building variations of it. */
    private suspend fun realDatabaseFile(): File {
        fillSource()
        val unpacked = temp.newFolder("unpacked")
        return (BackupZip().extract(backupOfSource().file, unpacked) as Try.Success).value.database
    }

    @Test
    fun `a library comes back from its backup with labels, progress and covers`() = runBlocking {
        val hobbitId = fillSource()
        targetBooks.add(
            testBook(title = "Old Book").copy(
                coverPath = cover(targetCovers, "old.jpg", "old picture").path,
            ),
        )

        val result = restorer().restore(backupOfSource().file)

        assertEquals(Try.Success(Unit), result)
        assertEquals(listOf("Dune", "The Hobbit"), titles(targetBooks))
        val restored = (targetBooks.getBook(hobbitId) as Try.Success).value
        assertEquals(listOf("J. R. R. Tolkien", "Second Author"), restored.authors)
        assertEquals(listOf("Fantasy"), restored.categories)
        assertEquals(listOf("favourite"), restored.tags)
        assertEquals("9780306406157", restored.isbn13)
        assertEquals(12, restored.currentPage)
        // The cover lives in this install's directory now, and the replaced library's is gone.
        assertEquals(File(targetCovers, "hobbit.jpg").path, restored.coverPath)
        assertEquals("hobbit picture", File(restored.coverPath!!).readText())
        assertFalse(File(targetCovers, "old.jpg").exists())
        // Search still finds restored books, so the full-text index followed the import.
        assertEquals(
            listOf("The Hobbit"),
            targetBooks.observeLibrary(LibraryFilter(query = "tolkien")).first().map { it.title },
        )
    }

    @Test
    fun `backing up changes nothing in the library`() = runBlocking {
        fillSource()

        backupOfSource()

        assertEquals(listOf("Dune", "The Hobbit"), titles(sourceBooks))
        assertTrue(File(sourceCovers, "hobbit.jpg").exists())
    }

    @Test
    fun `a backup describes what it holds`() = runBlocking {
        fillSource()

        val backup = backupOfSource()

        assertEquals(2, backup.info.bookCount)
        assertEquals(NOW, backup.info.createdAt)
        assertEquals(OtiumDatabase.VERSION, backup.info.schemaVersion)
        assertEquals(backup.file.length(), backup.info.sizeBytes)
        assertEquals(Try.Success(backup.info), archiver().inspect(backup.file))
    }

    @Test
    fun `an empty library still backs up, with no books`() {
        val backup = backupOfSource()

        assertEquals(0, backup.info.bookCount)
    }

    @Test
    fun `only covers in the covers directory are packed`() = runBlocking {
        val stray = cover(temp.newFolder("elsewhere"), "stray.jpg", "stray")
        sourceBooks.add(testBook(title = "Stray").copy(coverPath = stray.path))
        fillSource()

        val backup = backupOfSource()

        val names = ZipFile(backup.file).use { zip -> zip.entries().toList().map { it.name } }
        assertEquals(setOf("manifest.json", "library.db", "covers/hobbit.jpg"), names.toSet())
    }

    @Test
    fun `a file that is not a backup is reported as corrupt`() = runBlocking {
        val notABackup = File(temp.root, "notes.zip").apply { writeText("not a zip") }

        assertEquals(Try.Failure(AppError.CorruptBackup), archiver().inspect(notABackup))
        assertEquals(Try.Failure(AppError.CorruptBackup), restorer().restore(notABackup))
    }

    @Test
    fun `a damaged library file leaves the library and covers as they were`() = runBlocking {
        addKeeper()
        val damaged = File(temp.root, "damaged.db").apply { writeBytes(ByteArray(4_096) { 7 }) }
        val backup = craftedBackup(damaged, cover(temp.root, "new.jpg", "new picture"))

        val result = restorer().restore(backup)

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertEquals(listOf("Keep Me"), titles(targetBooks))
        assertEquals(listOf("keep.jpg"), targetCovers.list().orEmpty().toList())
    }

    @Test
    fun `a backup with schema objects the app never creates is refused`() = runBlocking {
        addKeeper()
        val extras = listOf(
            "CREATE TRIGGER wipe AFTER INSERT ON book BEGIN DELETE FROM tag; END",
            "CREATE VIEW everything AS SELECT * FROM book",
            "CREATE TABLE stowaway (payload TEXT)",
            "CREATE INDEX extra ON book (title)",
        )
        // One real two-book library, so the book count always matches and only the schema differs.
        val original = realDatabaseFile()
        for (statement in extras) {
            val databaseFile = original.copyTo(File(temp.newFolder(), "library.db"))
            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
                .use { it.execSQL(statement) }

            val result = restorer().restore(craftedBackup(databaseFile))

            assertEquals(statement, Try.Failure(AppError.CorruptBackup), result)
            assertEquals(listOf("Keep Me"), titles(targetBooks))
        }
    }

    @Test
    fun `a backup from a newer schema is refused and nothing changes`() = runBlocking {
        addKeeper()
        val newer = craftedBackup(realDatabaseFile(), schemaVersion = OtiumDatabase.VERSION + 1)

        val result = restorer().restore(newer)

        val expected = AppError.IncompatibleBackup(OtiumDatabase.VERSION + 1, OtiumDatabase.VERSION)
        assertEquals(Try.Failure(expected), result)
        assertEquals(listOf("Keep Me"), titles(targetBooks))
    }

    @Test
    fun `a failure while importing rolls the library back and removes copied covers`() =
        runBlocking {
            addKeeper()
            val databaseFile = realDatabaseFile()
            // A join row pointing at a label that does not exist: Room would never write one, so
            // it goes in through plain SQLite, which does not check foreign keys unless asked.
            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
                .use {
                    it.execSQL("INSERT INTO book_tag (bookId, tagId, position) VALUES (1, 999, 0)")
                }
            val broken = craftedBackup(databaseFile, cover(temp.root, "new.jpg", "new picture"))

            val result = restorer().restore(broken)

            assertTrue(result is Try.Failure && result.error is AppError.Storage)
            assertEquals(listOf("Keep Me"), titles(targetBooks))
            assertEquals(listOf("keep.jpg"), targetCovers.list().orEmpty().toList())
            val kept = targetBooks.observeLibrary(LibraryFilter()).first().single()
            assertEquals("keep picture", File(kept.coverPath!!).readText())
        }

    @Test
    fun `a backup in a newer layout is refused and nothing changes`() = runBlocking {
        addKeeper()
        val newer = craftedBackup(realDatabaseFile(), format = BackupManifest.FORMAT + 1)

        assertEquals(Try.Failure(AppError.CorruptBackup), archiver().inspect(newer))
        assertEquals(Try.Failure(AppError.CorruptBackup), restorer().restore(newer))
        assertEquals(listOf("Keep Me"), titles(targetBooks))
    }

    @Test
    fun `a backup holding other books than its manifest counts is refused`() = runBlocking {
        addKeeper()
        val miscounted = craftedBackup(realDatabaseFile(), bookCount = 3)

        val result = restorer().restore(miscounted)

        assertEquals(Try.Failure(AppError.CorruptBackup), result)
        assertEquals(listOf("Keep Me"), titles(targetBooks))
    }

    @Test
    fun `a cover already in place is kept as it is`() = runBlocking {
        val hobbitId = fillSource()
        cover(targetCovers, "hobbit.jpg", "picture already here")

        val result = restorer().restore(backupOfSource().file)

        assertEquals(Try.Success(Unit), result)
        val restored = (targetBooks.getBook(hobbitId) as Try.Success).value
        assertEquals("picture already here", File(restored.coverPath!!).readText())
    }

    @Test
    fun `a book whose cover the backup does not carry comes back without one`() = runBlocking {
        val stray = cover(temp.newFolder("elsewhere"), "stray.jpg", "stray")
        val added = sourceBooks.add(testBook(title = "Stray").copy(coverPath = stray.path))
        val strayId = (added as Try.Success).value

        val result = restorer().restore(backupOfSource().file)

        assertEquals(Try.Success(Unit), result)
        assertEquals(null, (targetBooks.getBook(strayId) as Try.Success).value.coverPath)
    }

    @Test
    fun `a restore with nowhere to unpack reports it and changes nothing`() = runBlocking {
        addKeeper()
        val backup = backupOfSource().file
        val blocked = File(temp.root, "blocked").apply { writeText("a file, not a folder") }
        val restorer = RoomLibraryRestorer(
            context = context,
            database = target,
            files = BackupFiles(blocked, targetCovers),
            dispatchers = RealDispatchers,
        )

        val result = restorer.restore(backup)

        assertTrue(result is Try.Failure && result.error is AppError.Storage)
        assertEquals(listOf("Keep Me"), titles(targetBooks))
        assertEquals(listOf("keep.jpg"), targetCovers.list().orEmpty().toList())
    }

    @Test
    fun `a backup that cannot read the library reports it and leaves no files behind`() =
        runBlocking {
            fillSource()
            source.close()

            val result = archiver().create()

            assertTrue("$result", result is Try.Failure && result.error is AppError.Storage)
            assertEquals(emptyList<String>(), File(temp.root, "work").list().orEmpty().toList())
        }

    @Test
    fun `a backup with nowhere to write reports it`() = runBlocking {
        fillSource()
        val blocked = File(temp.root, "blocked").apply { writeText("a file, not a folder") }
        val archiver = ZipBackupArchiver(
            context = context,
            database = source,
            files = BackupFiles(blocked, sourceCovers),
            clock = { NOW },
            appVersion = "0.1.0",
            dispatchers = RealDispatchers,
        )

        val result = archiver.create()

        assertTrue(result is Try.Failure && result.error is AppError.Storage)
    }

    /** A backup file built by hand, for contents the archiver would never produce. */
    private fun craftedBackup(
        databaseFile: File,
        vararg covers: File,
        schemaVersion: Int = OtiumDatabase.VERSION,
        bookCount: Int = 2,
        format: Int = BackupManifest.FORMAT,
    ): File {
        val manifest = BackupManifest(
            format = format,
            appVersion = "0.1.0",
            schemaVersion = schemaVersion,
            createdAt = NOW,
            bookCount = bookCount,
        )
        val target = File(temp.newFolder(), "crafted.zip")
        BackupZip().write(target, manifest, databaseFile, covers.toList())
        return target
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
