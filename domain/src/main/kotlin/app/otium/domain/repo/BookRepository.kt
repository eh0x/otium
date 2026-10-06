package app.otium.domain.repo

import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryFilter
import kotlinx.coroutines.flow.Flow

/** The library store. Room is the source of truth; implementations live in the app module. */
interface BookRepository {
    fun observeLibrary(filter: LibraryFilter): Flow<List<BookSummary>>

    fun observeBook(id: Long): Flow<Book?>

    suspend fun getBook(id: Long): Try<Book>

    suspend fun findByIsbn13(isbn13: String): Try<Book?>

    /** Fails with AppError.Duplicate when the ISBN already exists. Returns the new id. */
    suspend fun add(book: Book): Try<Long>

    /** Replaces the book's fields, including authors, categories and tags. */
    suspend fun update(book: Book): Try<Unit>

    suspend fun updateProgress(id: Long, currentPage: Int, updatedAt: Long): Try<Unit>

    suspend fun delete(id: Long): Try<Unit>

    fun observeCategories(): Flow<List<String>>

    fun observeTags(): Flow<List<String>>

    fun observeAuthors(): Flow<List<String>>
}
