package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLibraryFiles
import app.otium.domain.fakes.FakeLibraryJson
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookStatus
import app.otium.domain.transfer.ImportRow
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.transfer.Shelf
import app.otium.domain.transfer.fixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportLibraryTest {

    private val repo = FakeBookRepository()
    private val files = FakeLibraryFiles()
    private val json = FakeLibraryJson()
    private var scheduled = 0
    private val import = ImportLibrary(
        books = repo,
        readFile = ReadLibraryFile(files, json, Dispatchers.Unconfined),
        clock = FixedClock(now = 5_000L),
        scheduler = { scheduled++ },
        lock = BackupLock(),
    )

    private suspend fun preview(text: String): ImportPreview {
        files.files["content://file"] = text
        return (import.preview("content://file") as Try.Success).value
    }

    private suspend fun importText(text: String): ImportResult = import.import(preview(text))

    private fun titled(title: String) = repo.all.single { it.title == title }

    @Test
    fun `a goodreads file is previewed without writing anything`() = runTest {
        repo.seed(book(isbn13 = "9780141439518", title = "Already here"))

        val preview = preview(fixture("goodreads_library_export.csv"))

        assertEquals(LibraryFormat.GOODREADS, preview.format)
        assertEquals(4, preview.rows.size)
        assertEquals(3, preview.newBooks)
        assertEquals(1, preview.alreadyInLibrary)
        assertEquals(1, preview.withoutIsbn)
        assertEquals(0, preview.unreadable)
        assertEquals(1, repo.all.size)
    }

    @Test
    fun `a storygraph file is recognised`() = runTest {
        val preview = preview(fixture("storygraph_export.csv"))

        assertEquals(LibraryFormat.STORYGRAPH, preview.format)
        assertEquals(4, preview.newBooks)
        assertEquals("a made-up id and a missing isbn are not isbns", 2, preview.withoutIsbn)
    }

    @Test
    fun `an isbn that repeats in the file counts once`() = runTest {
        val preview = preview(
            "Title,Author,Exclusive Shelf,ISBN13\nA,X,read,9780306406157\nB,Y,read,9780306406157\n",
        )

        assertEquals(1, preview.newBooks)
        assertEquals(1, preview.alreadyInLibrary)
    }

    @Test
    fun `rows without a title are left out`() = runTest {
        val preview = preview("Title,Author,Exclusive Shelf\n,Nobody,read\nKept,Somebody,read\n")

        assertEquals(1, preview.unreadable)
        assertEquals(listOf("Kept"), preview.rows.map { it.title })
    }

    @Test
    fun `a file in no known format is refused`() = runTest {
        files.files["content://file"] = "Name,Price\nTea,3\n"

        assertEquals(Try.Failure(AppError.UnknownFileFormat), import.preview("content://file"))
    }

    @Test
    fun `an empty file is refused`() = runTest {
        files.files["content://file"] = ""

        assertEquals(Try.Failure(AppError.UnknownFileFormat), import.preview("content://file"))
    }

    @Test
    fun `a file that cannot be read reports why`() = runTest {
        assertEquals(
            Try.Failure(AppError.InvalidInput("file")),
            import.preview("content://missing"),
        )
    }

    @Test
    fun `importing adds the books and plans one lookup for those with an isbn`() = runTest {
        val result = importText(fixture("goodreads_library_export.csv"))

        assertEquals(ImportResult(added = 4, skipped = 0, failed = 0, waitingForLookup = 3), result)
        assertEquals(1, scheduled)
        val pride = titled("Pride and Prejudice")
        assertEquals("9780141439518", pride.isbn13)
        assertEquals("0141439513", pride.isbn10)
        assertTrue(pride.needsLookup)
        assertFalse(pride.userEdited)
        assertEquals(1_577_145_600_000L, pride.addedAt)
        assertEquals(5_000L, pride.updatedAt)
        assertFalse("no isbn, nothing to look up", titled("Good Omens").needsLookup)
    }

    @Test
    fun `shelves become progress when the page count is known`() = runTest {
        importText(fixture("goodreads_library_export.csv"))

        assertEquals(BookStatus.READ, titled("Pride and Prejudice").status)
        assertEquals(1, titled("The Hobbit").currentPage)
        assertEquals(BookStatus.IN_PROGRESS, titled("The Hobbit").status)
        assertEquals(BookStatus.NOT_STARTED, titled("The Way of Kings").status)
    }

    @Test
    fun `without a page count a shelf is kept as a tag`() = runTest {
        importText(fixture("storygraph_export.csv"))

        assertEquals(listOf("classics", "romance", "read"), titled("Pride and Prejudice").tags)
        assertEquals(listOf("currently-reading"), titled("The Hobbit").tags)
        assertEquals(emptyList<String>(), titled("Good Omens").tags)
        assertNull("a made-up id is not kept as an isbn", titled("Unfinished Thing").isbn13)
    }

    @Test
    fun `importing the same file twice adds nothing the second time`() = runTest {
        val text = fixture("goodreads_library_export.csv")
        importText(text)

        val again = importText(text)

        assertEquals("Good Omens has no isbn, so it cannot be recognised", 1, again.added)
        assertEquals(3, again.skipped)
    }

    @Test
    fun `progress is reported after every row`() = runTest {
        val seen = mutableListOf<Pair<Int, Int>>()

        import.import(preview(fixture("goodreads_library_export.csv"))) { done, total ->
            seen += done to total
        }

        assertEquals(listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), seen)
    }

    @Test
    fun `books that cannot be stored are counted and the rest still go in`() = runTest {
        val preview = preview(fixture("goodreads_library_export.csv"))
        repo.failWith = AppError.Storage("disk full")

        val result = import.import(preview)

        assertEquals(ImportResult(added = 0, skipped = 0, failed = 4, waitingForLookup = 0), result)
        assertEquals(0, scheduled)
    }

    @Test
    fun `an otium file keeps its details and is not looked up again`() = runTest {
        json.rows = listOf(
            ImportRow(
                title = "Kept whole",
                isbn = "9780306406157",
                totalPages = 320,
                currentPage = 99,
                shelf = Shelf.READ,
                categories = listOf("History"),
                description = "Long.",
                complete = true,
                userEdited = true,
            ),
        )

        val preview = preview("{\"books\":[]}")
        val result = import.import(preview)

        assertEquals(LibraryFormat.OTIUM, preview.format)
        assertEquals(0, result.waitingForLookup)
        assertEquals(0, scheduled)
        val kept = titled("Kept whole")
        assertEquals("the exact page wins over the shelf", 99, kept.currentPage)
        assertEquals(listOf("History"), kept.categories)
        assertEquals("Long.", kept.description)
        assertTrue(kept.userEdited)
        assertFalse(kept.needsLookup)
    }

    @Test
    fun `an isbn-10 is kept only when it is valid`() = runTest {
        json.rows = listOf(
            ImportRow(title = "Valid", isbn = "9780306406157", isbn10 = "0306406152"),
            ImportRow(title = "Typo", isbn = "9780141439518", isbn10 = "0306406153"),
        )

        import.import(preview("{}"))

        assertEquals("0306406152", titled("Valid").isbn10)
        assertNull(titled("Typo").isbn10)
    }

    @Test
    fun `an exact page beyond the book is kept within it`() = runTest {
        json.rows = listOf(ImportRow(title = "Short", totalPages = 10, currentPage = 50))

        import.import(preview("{}"))

        assertEquals(10, titled("Short").currentPage)
    }
}
