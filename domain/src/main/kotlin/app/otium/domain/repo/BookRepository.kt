package app.otium.domain.repo

import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryFilter
import kotlinx.coroutines.flow.Flow

/**
 * The library store. Room is the source of truth; implementations live in the app module.
 *
 * The store owns the cover files its books name. A cover path handed to [add] or [update] (one
 * the [app.otium.domain.port.CoverStore] returned) belongs to the store from then on: once a
 * write leaves a cover that no book names, because it was replaced, its book was deleted, or the
 * write that brought it failed, the store deletes it. Callers never delete covers themselves.
 */
interface BookRepository {
    fun observeLibrary(filter: LibraryFilter): Flow<List<BookSummary>>

    /** The number of books in the library, ignoring any filter. Drives the empty states. */
    fun observeCount(): Flow<Int>

    fun observeBook(id: Long): Flow<Book?>

    suspend fun getBook(id: Long): Try<Book>

    suspend fun findByIsbn13(isbn13: String): Try<Book?>

    /** Books saved offline from an ISBN alone, oldest first, still waiting for their details. */
    suspend fun getBooksNeedingLookup(): Try<List<Book>>

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
