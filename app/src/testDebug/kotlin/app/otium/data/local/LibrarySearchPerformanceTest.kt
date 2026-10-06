package app.otium.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import app.otium.data.repo.BookRepositoryImpl
import app.otium.debug.SyntheticBooks
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.SortOrder
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the "search under 100 ms at about 5,000 books" budget against regressions.
 * This runs on the host JVM with Robolectric's native SQLite, so it is a regression
 * guard, not a device measurement. Confirm on a real device in Phase 3.
 */
@RunWith(RobolectricTestRunner::class)
class LibrarySearchPerformanceTest {

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

    private suspend fun seed(count: Int) {
        val books = SyntheticBooks.generate(count)
        db.withTransaction { books.forEach { repo.add(it) } }
    }

    /** Median wall time in ms over several runs, after one warm-up. */
    private suspend fun medianMillis(filter: LibraryFilter, runs: Int = 7): Pair<Long, Int> {
        var size = repo.observeLibrary(filter).first().size
        val times = List(runs) {
            measureTimeMillis { size = repo.observeLibrary(filter).first().size }
        }.sorted()
        return times[times.size / 2] to size
    }

    @Test
    fun `seeds five thousand valid books`() = runTest {
        seed(5_000)

        assertEquals(5_000, repo.observeLibrary(LibraryFilter()).first().size)
    }

    @Test
    fun `text search stays within the latency budget`() = runTest {
        seed(5_000)

        val (prefix, prefixCount) = medianMillis(LibraryFilter(query = "silen"))
        val (words, wordsCount) = medianMillis(LibraryFilter(query = "golden river"))
        val (author, authorCount) = medianMillis(LibraryFilter(query = "lindq"))

        println("search ms: prefix=$prefix ($prefixCount rows) words=$words ($wordsCount rows) author=$author ($authorCount rows)")
        assertTrue("prefix search matched nothing", prefixCount > 0)
        assertTrue("prefix search took ${prefix}ms", prefix < 100)
        assertTrue("two word search took ${words}ms", words < 100)
        assertTrue("author search took ${author}ms", author < 100)
    }

    @Test
    fun `combined filters and sorts stay within the latency budget`() = runTest {
        seed(5_000)

        val combined = LibraryFilter(
            query = "quiet",
            status = BookStatus.IN_PROGRESS,
            categories = setOf("Fiction", "History"),
            tags = setOf("favourite"),
            sort = SortOrder.PROGRESS,
        )
        val (combinedMs, _) = medianMillis(combined)
        val (allByProgress, allCount) = medianMillis(LibraryFilter(sort = SortOrder.PROGRESS))
        val (allByTitle, _) = medianMillis(LibraryFilter(sort = SortOrder.TITLE))

        println("filter ms: combined=$combinedMs all-by-progress=$allByProgress ($allCount rows) all-by-title=$allByTitle")
        assertTrue("combined filter took ${combinedMs}ms", combinedMs < 100)
        assertTrue("full list by progress took ${allByProgress}ms", allByProgress < 100)
        assertTrue("full list by title took ${allByTitle}ms", allByTitle < 100)
    }
}
