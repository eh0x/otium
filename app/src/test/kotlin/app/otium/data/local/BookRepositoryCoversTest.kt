package app.otium.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.otium.data.cover.CoverFiles
import app.otium.data.repo.RoomBookRepository
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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

/** The repository owns the cover files its books name: these are the rules for deleting them. */
@RunWith(RobolectricTestRunner::class)
class BookRepositoryCoversTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var db: OtiumDatabase
    private lateinit var repo: RoomBookRepository
    private val coversDir by lazy { temp.newFolder("covers") }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtiumDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = RoomBookRepository(db.bookDao(), CoverFiles(coversDir), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun coverFile(name: String): File = File(coversDir, name).apply { writeText("picture") }

    private suspend fun add(cover: File?, isbn13: String? = null): Book {
        val book = testBook(isbn13 = isbn13).copy(coverPath = cover?.path)
        val id = (repo.add(book) as Try.Success).value
        return (repo.getBook(id) as Try.Success).value
    }

    /** Gives book [id] a cover path as rows from before names were stored hold it. */
    private fun setOldStylePath(id: Long, name: String) {
        db.openHelper.writableDatabase.execSQL(
            "UPDATE book SET coverPath = '/data/old/install/files/covers/$name' WHERE id = $id",
        )
    }

    @Test
    fun `deleting a book deletes its cover`() = runTest {
        val cover = coverFile("a.jpg")
        val book = add(cover)

        assertEquals(Try.Success(Unit), repo.delete(book.id))

        assertFalse(cover.exists())
    }

    @Test
    fun `an update with a new cover deletes the old one`() = runTest {
        val old = coverFile("old.jpg")
        val new = coverFile("new.jpg")
        val book = add(old)

        assertEquals(Try.Success(Unit), repo.update(book.copy(coverPath = new.path)))

        assertFalse(old.exists())
        assertTrue(new.exists())
    }

    @Test
    fun `an update that removes the cover deletes it`() = runTest {
        val cover = coverFile("a.jpg")
        val book = add(cover)

        repo.update(book.copy(coverPath = null))

        assertFalse(cover.exists())
    }

    @Test
    fun `an update that keeps the cover keeps the file`() = runTest {
        val cover = coverFile("a.jpg")
        val book = add(cover)

        repo.update(book.copy(title = "Renamed"))

        assertTrue(cover.exists())
    }

    @Test
    fun `an add that fails deletes the cover it brought`() = runTest {
        add(coverFile("first.jpg"), isbn13 = "9780306406157")
        val brought = coverFile("second.jpg")

        val result = repo.add(testBook(isbn13 = "9780306406157").copy(coverPath = brought.path))

        assertTrue(result is Try.Failure)
        assertFalse(brought.exists())
    }

    @Test
    fun `an update that fails deletes the new cover and keeps the old one`() = runTest {
        add(null, isbn13 = "9780306406157")
        val old = coverFile("old.jpg")
        val book = add(old, isbn13 = "9780441013593")
        val new = coverFile("new.jpg")

        // Taking the other book's ISBN breaks the unique constraint, so nothing is written.
        val result = repo.update(book.copy(isbn13 = "9780306406157", coverPath = new.path))

        assertTrue(result is Try.Failure)
        assertTrue(old.exists())
        assertFalse(new.exists())
    }

    @Test
    fun `an update that fails keeps an unchanged cover`() = runTest {
        add(null, isbn13 = "9780306406157")
        val cover = coverFile("a.jpg")
        val book = add(cover, isbn13 = "9780441013593")

        val result = repo.update(book.copy(isbn13 = "9780306406157"))

        assertTrue(result is Try.Failure)
        assertTrue(cover.exists())
    }

    @Test
    fun `a cover another book still names is kept`() = runTest {
        val shared = coverFile("shared.jpg")
        val first = add(shared)
        add(shared)

        repo.delete(first.id)

        assertTrue(shared.exists())
    }

    @Test
    fun `a cover an older row names by its full path is kept`() = runTest {
        val shared = coverFile("shared.jpg")
        val first = add(shared)
        setOldStylePath(add(null).id, "shared.jpg")

        repo.delete(first.id)

        assertTrue(shared.exists())
    }

    @Test
    fun `deleting a book from an older row deletes its cover`() = runTest {
        val cover = coverFile("a.jpg")
        val book = add(null)
        setOldStylePath(book.id, "a.jpg")

        repo.delete(book.id)

        assertFalse(cover.exists())
    }
}
