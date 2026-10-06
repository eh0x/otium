package app.otium.domain.fakes

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookSource
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryFilter
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository
import app.otium.domain.usecase.DeriveStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [BookRepository] for use case tests. Filtering and sorting are not simulated. */
class FakeBookRepository : BookRepository {
    private val books = MutableStateFlow<Map<Long, Book>>(emptyMap())
    private var nextId = 1L

    var lastFilter: LibraryFilter? = null
        private set

    /** When set, every write (add, update, updateProgress, delete) fails with this error. */
    var failWith: AppError? = null

    /** Every filter passed to observeLibrary, oldest first. */
    val filters = mutableListOf<LibraryFilter>()
    var lastProgressUpdate: Triple<Long, Int, Long>? = null
        private set

    fun seed(book: Book): Book {
        val stored = if (book.id == 0L) book.copy(id = nextId++) else book
        books.value = books.value + (stored.id to stored)
        return stored
    }

    val all: List<Book> get() = books.value.values.toList()

    /**
     * Approximates the real library query: text, status, any-of categories and tags, author, and
     * title sorting. The Room tests are authoritative; this is enough for ViewModel behavior.
     */
    override fun observeLibrary(filter: LibraryFilter): Flow<List<BookSummary>> {
        lastFilter = filter
        filters += filter
        return books.map { map ->
            map.values
                .filter { it.matches(filter) }
                .sortedWith(filter.sort.comparator())
                .map {
                    BookSummary(
                        id = it.id,
                        title = it.title,
                        authors = it.authors.joinToString(", "),
                        coverPath = it.coverPath,
                        coverSourceUrl = it.coverSourceUrl,
                        currentPage = it.currentPage,
                        totalPages = it.totalPages,
                        status = DeriveStatus(it.currentPage, it.totalPages),
                    )
                }
        }
    }

    override fun observeCount(): Flow<Int> = books.map { it.size }

    override fun observeBook(id: Long): Flow<Book?> = books.map { it[id] }

    override suspend fun getBook(id: Long): Try<Book> =
        books.value[id]?.let { Try.Success(it) } ?: Try.Failure(AppError.NotFound)

    override suspend fun findByIsbn13(isbn13: String): Try<Book?> =
        Try.Success(books.value.values.firstOrNull { it.isbn13 == isbn13 })

    override suspend fun add(book: Book): Try<Long> {
        failWith?.let { return Try.Failure(it) }
        val existing = book.isbn13?.let { isbn ->
            books.value.values.firstOrNull { it.isbn13 == isbn }
        }
        if (existing != null) return Try.Failure(AppError.Duplicate(existing.id))
        return Try.Success(seed(book).id)
    }

    override suspend fun update(book: Book): Try<Unit> {
        failWith?.let { return Try.Failure(it) }
        if (book.id !in books.value) return Try.Failure(AppError.NotFound)
        books.value = books.value + (book.id to book)
        return Try.Success(Unit)
    }

    override suspend fun updateProgress(id: Long, currentPage: Int, updatedAt: Long): Try<Unit> {
        failWith?.let { return Try.Failure(it) }
        val book = books.value[id] ?: return Try.Failure(AppError.NotFound)
        lastProgressUpdate = Triple(id, currentPage, updatedAt)
        books.value =
            books.value + (id to book.copy(currentPage = currentPage, updatedAt = updatedAt))
        return Try.Success(Unit)
    }

    override suspend fun delete(id: Long): Try<Unit> {
        failWith?.let { return Try.Failure(it) }
        if (id !in books.value) return Try.Failure(AppError.NotFound)
        books.value = books.value - id
        return Try.Success(Unit)
    }

    override fun observeCategories(): Flow<List<String>> =
        books.map { map -> map.values.flatMap { it.categories }.distinct().sorted() }

    override fun observeTags(): Flow<List<String>> =
        books.map { map -> map.values.flatMap { it.tags }.distinct().sorted() }

    override fun observeAuthors(): Flow<List<String>> =
        books.map { map -> map.values.flatMap { it.authors }.distinct().sorted() }
}

class FixedClock(var now: Long = 1_000L) : Clock {
    override fun nowMillis(): Long = now
}

fun book(
    id: Long = 0L,
    isbn13: String? = null,
    title: String = "A Book",
    totalPages: Int = 300,
    currentPage: Int = 0,
    categories: List<String> = emptyList(),
    tags: List<String> = emptyList(),
) = Book(
    id = id,
    isbn13 = isbn13,
    isbn10 = null,
    title = title,
    subtitle = null,
    authors = listOf("Some Author"),
    publisher = null,
    publishedYear = null,
    language = null,
    description = null,
    totalPages = totalPages,
    currentPage = currentPage,
    categories = categories,
    tags = tags,
    coverPath = null,
    coverSourceUrl = null,
    source = BookSource.MANUAL,
    addedAt = 1L,
    updatedAt = 1L,
)
