package app.otium.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.otium.data.cover.CoverFiles
import app.otium.data.repo.RoomBookRepository
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookFormat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The format label (D35) in Room: stored, changed, cleared, and read safely. */
@RunWith(RobolectricTestRunner::class)
class BookRepositoryFormatTest {

    private lateinit var db: OtiumDatabase
    private lateinit var repo: RoomBookRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtiumDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = RoomBookRepository(db.bookDao(), CoverFiles(File("covers")), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun add(book: Book): Long = (repo.add(book) as Try.Success).value

    private suspend fun formatOf(id: Long): BookFormat? =
        (repo.getBook(id) as Try.Success).value.format

    @Test
    fun `a book keeps its format, and a book without one stays unset`() = runTest {
        val heard = add(
            testBook(title = "Heard", isbn13 = "9780306406157")
                .copy(format = BookFormat.AUDIOBOOK),
        )
        val plain = add(testBook(title = "Plain", isbn13 = "9780141439518"))

        assertEquals(BookFormat.AUDIOBOOK, formatOf(heard))
        assertNull(formatOf(plain))
    }

    @Test
    fun `updating a book can change and clear its format`() = runTest {
        val id = add(testBook().copy(format = BookFormat.PRINT))
        val book = (repo.getBook(id) as Try.Success).value

        repo.update(book.copy(format = BookFormat.EBOOK))
        assertEquals(BookFormat.EBOOK, formatOf(id))

        repo.update(book.copy(format = null))
        assertNull(formatOf(id))
    }

    @Test
    fun `a format this app does not know reads as unset`() = runTest {
        val id = add(testBook())
        db.openHelper.writableDatabase.execSQL("UPDATE book SET format = 'VINYL' WHERE id = $id")

        assertNull(formatOf(id))
    }
}
