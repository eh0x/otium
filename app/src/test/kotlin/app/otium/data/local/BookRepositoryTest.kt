package app.otium.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.otium.data.repo.BookRepositoryImpl
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.SortOrder
import app.otium.domain.usecase.DeriveStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookRepositoryTest {

    private lateinit var db: OtiumDatabase
    private lateinit var repo: BookRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OtiumDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = BookRepositoryImpl(db.bookDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun add(book: Book): Long = (repo.add(book) as Try.Success).value

    private suspend fun titles(filter: LibraryFilter = LibraryFilter()): List<String> =
        repo.observeLibrary(filter).first().map { it.title }

    private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    // --- CRUD ---

    @Test
    fun `add then get returns every field and ordered labels`() = runTest {
        val id = add(
            testBook(
                title = "The Hobbit",
                subtitle = "There and Back Again",
                authors = listOf("J. R. R. Tolkien", "Second Author"),
                isbn13 = "9780306406157",
                categories = listOf("Fantasy", "Classic"),
                tags = listOf("favourite"),
                totalPages = 310,
                currentPage = 12,
                addedAt = 100L,
                updatedAt = 200L,
            ),
        )

        val book = (repo.getBook(id) as Try.Success).value

        assertEquals(id, book.id)
        assertEquals("The Hobbit", book.title)
        assertEquals("There and Back Again", book.subtitle)
        assertEquals(listOf("J. R. R. Tolkien", "Second Author"), book.authors)
        assertEquals("9780306406157", book.isbn13)
        assertEquals(setOf("Fantasy", "Classic"), book.categories.toSet())
        assertEquals(listOf("favourite"), book.tags)
        assertEquals(310, book.totalPages)
        assertEquals(12, book.currentPage)
        assertEquals(100L, book.addedAt)
        assertEquals(200L, book.updatedAt)
        assertEquals("Publisher", book.publisher)
        assertEquals(1999, book.publishedYear)
    }

    @Test
    fun `getBook reports not found for an unknown id`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), repo.getBook(404L))
    }

    @Test
    fun `duplicate isbn reports the existing book`() = runTest {
        val first = add(testBook(title = "First", isbn13 = "9780306406157"))

        val second = repo.add(testBook(title = "Second", isbn13 = "9780306406157"))

        assertEquals(Try.Failure(AppError.Duplicate(first)), second)
        assertEquals(listOf("First"), titles())
    }

    @Test
    fun `several books without an isbn are allowed`() = runTest {
        add(testBook(title = "A"))
        add(testBook(title = "B"))

        assertEquals(listOf("A", "B"), titles())
    }

    @Test
    fun `findByIsbn13 finds the book or returns null`() = runTest {
        val id = add(testBook(title = "A", isbn13 = "9780306406157"))

        assertEquals(id, (repo.findByIsbn13("9780306406157") as Try.Success).value?.id)
        assertNull((repo.findByIsbn13("9791032305690") as Try.Success).value)
    }

    @Test
    fun `update replaces fields and labels and drops unused labels`() = runTest {
        val id =
            add(
                testBook(
                    title = "Old",
                    authors = listOf("Old Author"),
                    categories = listOf("OldCat"),
                    tags = listOf("oldtag"),
                ),
            )
        val stored = (repo.getBook(id) as Try.Success).value

        val result = repo.update(
            stored.copy(
                title = "New",
                authors = listOf("New Author"),
                categories = listOf("NewCat"),
                tags = listOf("newtag"),
                updatedAt = 999L,
            ),
        )

        assertTrue(result is Try.Success)
        val updated = (repo.getBook(id) as Try.Success).value
        assertEquals("New", updated.title)
        assertEquals(listOf("New Author"), updated.authors)
        assertEquals(listOf("NewCat"), updated.categories)
        assertEquals(listOf("newtag"), updated.tags)
        assertEquals(999L, updated.updatedAt)
        assertEquals(listOf("NewCat"), repo.observeCategories().first())
        assertEquals(listOf("newtag"), repo.observeTags().first())
        assertEquals(listOf("New Author"), repo.observeAuthors().first())
    }

    @Test
    fun `update keeps the search index in sync`() = runTest {
        val id = add(testBook(title = "Dune"))
        val stored = (repo.getBook(id) as Try.Success).value

        repo.update(stored.copy(title = "Hyperion"))

        assertEquals(listOf("Hyperion"), titles(LibraryFilter(query = "hyper")))
        assertTrue(titles(LibraryFilter(query = "dune")).isEmpty())
    }

    @Test
    fun `update of a missing book reports not found`() = runTest {
        assertEquals(
            Try.Failure(AppError.NotFound),
            repo.update(testBook(title = "Ghost").copy(id = 77L)),
        )
    }

    @Test
    fun `updateProgress stores page and timestamp`() = runTest {
        val id = add(testBook(totalPages = 300))

        val result = repo.updateProgress(id, currentPage = 120, updatedAt = 4_000L)

        assertTrue(result is Try.Success)
        val book = (repo.getBook(id) as Try.Success).value
        assertEquals(120, book.currentPage)
        assertEquals(4_000L, book.updatedAt)
    }

    @Test
    fun `updateProgress on a missing book reports not found`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), repo.updateProgress(5L, 10, 1L))
    }

    @Test
    fun `delete removes the book and its unused labels`() = runTest {
        val id =
            add(
                testBook(
                    title = "Gone",
                    authors = listOf("Solo"),
                    categories = listOf("Cat"),
                    tags = listOf("t"),
                ),
            )
        add(testBook(title = "Stays", authors = listOf("Other"), categories = listOf("Cat")))

        val result = repo.delete(id)

        assertTrue(result is Try.Success)
        assertEquals(listOf("Stays"), titles())
        assertEquals(listOf("Other"), repo.observeAuthors().first())
        assertEquals(listOf("Cat"), repo.observeCategories().first())
        assertTrue(repo.observeTags().first().isEmpty())
        assertEquals(Try.Failure(AppError.NotFound), repo.getBook(id))
    }

    @Test
    fun `delete of a missing book reports not found`() = runTest {
        assertEquals(Try.Failure(AppError.NotFound), repo.delete(9L))
    }

    @Test
    fun `labels are shared case insensitively between books`() = runTest {
        add(testBook(title = "A", authors = listOf("Tolkien"), tags = listOf("Fav")))
        add(testBook(title = "B", authors = listOf("tolkien"), tags = listOf("fav")))

        assertEquals(1, repo.observeAuthors().first().size)
        assertEquals(1, repo.observeTags().first().size)
    }

    // --- Search ---

    private suspend fun seedSearchLibrary() {
        add(
            testBook(
                title = "The Hobbit",
                subtitle = "There and Back Again",
                authors = listOf("J. R. R. Tolkien"),
            ),
        )
        add(testBook(title = "Cien años de soledad", authors = listOf("Gabriel García Márquez")))
        add(testBook(title = "Dune", authors = listOf("Frank Herbert")))
    }

    @Test
    fun `search matches title prefix`() = runTest {
        seedSearchLibrary()
        assertEquals(listOf("The Hobbit"), titles(LibraryFilter(query = "hobb")))
        assertEquals(listOf("Dune"), titles(LibraryFilter(query = "du")))
    }

    @Test
    fun `search matches subtitle and author`() = runTest {
        seedSearchLibrary()
        assertEquals(listOf("The Hobbit"), titles(LibraryFilter(query = "back")))
        assertEquals(listOf("The Hobbit"), titles(LibraryFilter(query = "tolk")))
    }

    @Test
    fun `search ignores case and diacritics`() = runTest {
        seedSearchLibrary()
        assertEquals(listOf("The Hobbit"), titles(LibraryFilter(query = "HOBBIT")))
        assertEquals(listOf("Cien años de soledad"), titles(LibraryFilter(query = "garcia")))
        assertEquals(listOf("Cien años de soledad"), titles(LibraryFilter(query = "MARQUEZ")))
    }

    @Test
    fun `search words must all match`() = runTest {
        seedSearchLibrary()
        assertEquals(listOf("The Hobbit"), titles(LibraryFilter(query = "hobbit again")))
        assertTrue(titles(LibraryFilter(query = "hobbit dune")).isEmpty())
    }

    @Test
    fun `search with no match is empty and blank search returns everything`() = runTest {
        seedSearchLibrary()
        assertTrue(titles(LibraryFilter(query = "zzzz")).isEmpty())
        assertEquals(3, titles(LibraryFilter(query = "   ")).size)
        assertEquals(3, titles(LibraryFilter(query = "!!!")).size)
    }

    @Test
    fun `hostile search text matches nothing and leaves the library intact`() = runTest {
        seedSearchLibrary()

        assertTrue(titles(LibraryFilter(query = "'; DROP TABLE book;--")).isEmpty())
        assertEquals(3, titles().size)
    }

    // --- Filters ---

    @Test
    fun `status filter agrees with DeriveStatus for boundary values`() = runTest {
        val pages = listOf(0, 1, 5, 9, 10, 11)
        val totals = listOf(0, 1, 10)
        for (total in totals) {
            for (current in pages) {
                add(
                    testBook(
                        title = "b-$current-of-$total",
                        totalPages = total,
                        currentPage = current,
                    ),
                )
            }
        }

        for (status in BookStatus.entries) {
            val expected = totals.flatMap { total ->
                pages.filter { DeriveStatus(it, total) == status }.map { "b-$it-of-$total" }
            }.toSet()

            val actual = titles(LibraryFilter(status = status)).toSet()

            assertEquals("status $status", expected, actual)
        }
    }

    @Test
    fun `summaries carry the derived status`() = runTest {
        add(testBook(title = "Done", totalPages = 100, currentPage = 100))

        val summary = repo.observeLibrary(LibraryFilter()).first().single()

        assertEquals(BookStatus.READ, summary.status)
        assertEquals("Author", summary.authors)
    }

    @Test
    fun `category filter matches any selected category`() = runTest {
        add(testBook(title = "A", categories = listOf("Fiction")))
        add(testBook(title = "B", categories = listOf("Poetry")))
        add(testBook(title = "C", categories = listOf("History")))

        assertEquals(
            listOf("A", "B"),
            titles(LibraryFilter(categories = setOf("Fiction", "Poetry"))),
        )
        assertEquals(listOf("C"), titles(LibraryFilter(categories = setOf("history"))))
    }

    @Test
    fun `tag filter matches any selected tag`() = runTest {
        add(testBook(title = "A", tags = listOf("favourite", "lent")))
        add(testBook(title = "B", tags = listOf("lent")))
        add(testBook(title = "C"))

        assertEquals(listOf("A"), titles(LibraryFilter(tags = setOf("favourite"))))
        assertEquals(listOf("A", "B"), titles(LibraryFilter(tags = setOf("lent", "favourite"))))
    }

    @Test
    fun `author filter matches one author of a multi author book`() = runTest {
        add(testBook(title = "A", authors = listOf("Pratchett", "Gaiman")))
        add(testBook(title = "B", authors = listOf("Gaiman")))
        add(testBook(title = "C", authors = listOf("Herbert")))

        assertEquals(listOf("A", "B"), titles(LibraryFilter(author = "Gaiman")))
        assertEquals(listOf("A"), titles(LibraryFilter(author = "Pratchett")))
    }

    @Test
    fun `different filters combine with AND`() = runTest {
        add(
            testBook(
                title = "Match",
                authors = listOf("Tolkien"),
                categories = listOf("Fantasy"),
                tags = listOf("fav"),
                totalPages = 100,
                currentPage = 50,
            ),
        )
        add(
            testBook(
                title = "WrongStatus",
                authors = listOf("Tolkien"),
                categories = listOf("Fantasy"),
                tags = listOf("fav"),
                totalPages = 100,
                currentPage = 0,
            ),
        )
        add(
            testBook(
                title = "WrongTag",
                authors = listOf("Tolkien"),
                categories = listOf("Fantasy"),
                tags = listOf("other"),
                totalPages = 100,
                currentPage = 50,
            ),
        )
        add(
            testBook(
                title = "WrongAuthor",
                authors = listOf("Other"),
                categories = listOf("Fantasy"),
                tags = listOf("fav"),
                totalPages = 100,
                currentPage = 50,
            ),
        )

        val result = titles(
            LibraryFilter(
                query = "match",
                status = BookStatus.IN_PROGRESS,
                categories = setOf("Fantasy"),
                tags = setOf("fav"),
                author = "Tolkien",
            ),
        )

        assertEquals(listOf("Match"), result)
    }

    // --- Sorting ---

    private suspend fun seedSortLibrary() {
        add(
            testBook(
                title = "banana",
                authors = listOf("Zed"),
                addedAt = 3,
                updatedAt = 1,
                totalPages = 100,
                currentPage = 50,
            ),
        )
        add(
            testBook(
                title = "Apple",
                authors = listOf("Yan"),
                addedAt = 1,
                updatedAt = 3,
                totalPages = 100,
                currentPage = 10,
            ),
        )
        add(
            testBook(
                title = "cherry",
                authors = listOf("Xi"),
                addedAt = 2,
                updatedAt = 2,
                totalPages = 100,
                currentPage = 100,
            ),
        )
    }

    @Test
    fun `sort by title ignores case`() = runTest {
        seedSortLibrary()
        assertEquals(
            listOf("Apple", "banana", "cherry"),
            titles(LibraryFilter(sort = SortOrder.TITLE)),
        )
    }

    @Test
    fun `sort by author`() = runTest {
        seedSortLibrary()
        assertEquals(
            listOf("cherry", "Apple", "banana"),
            titles(LibraryFilter(sort = SortOrder.AUTHOR)),
        )
    }

    @Test
    fun `sort by recently added and last updated are newest first`() = runTest {
        seedSortLibrary()
        assertEquals(
            listOf("banana", "cherry", "Apple"),
            titles(LibraryFilter(sort = SortOrder.RECENTLY_ADDED)),
        )
        assertEquals(
            listOf("Apple", "cherry", "banana"),
            titles(LibraryFilter(sort = SortOrder.LAST_UPDATED)),
        )
    }

    @Test
    fun `sort by progress puts the most read first`() = runTest {
        seedSortLibrary()
        assertEquals(
            listOf("cherry", "banana", "Apple"),
            titles(LibraryFilter(sort = SortOrder.PROGRESS)),
        )
    }

    // --- Observation ---

    @Test
    fun `library flow re-emits when progress changes`() = runTest {
        val id = add(testBook(title = "A", totalPages = 100))

        repo.observeLibrary(LibraryFilter()).test {
            assertEquals(BookStatus.NOT_STARTED, awaitItem().single().status)

            repo.updateProgress(id, 100, 10L)

            assertEquals(
                BookStatus.READ,
                awaitUntil {
                    it.single().status == BookStatus.READ
                }.single().status,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library flow re-emits when a book is added`() = runTest {
        repo.observeLibrary(LibraryFilter()).test {
            assertTrue(awaitItem().isEmpty())

            add(testBook(title = "New"))

            assertEquals("New", awaitUntil { it.isNotEmpty() }.single().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeBook emits the book then null after deletion`() = runTest {
        val id = add(testBook(title = "Temp"))

        repo.observeBook(id).test {
            assertEquals("Temp", awaitItem()?.title)

            repo.delete(id)

            assertNull(awaitUntil { it == null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeBook for an unknown id emits null`() = runTest {
        assertNull(repo.observeBook(123L).first())
    }
}
