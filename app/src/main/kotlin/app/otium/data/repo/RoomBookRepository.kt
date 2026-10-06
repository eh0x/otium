package app.otium.data.repo

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import app.otium.data.local.LibraryQueryBuilder
import app.otium.data.local.dao.BookDao
import app.otium.data.local.mapper.toDomain
import app.otium.data.local.mapper.toEntity
import app.otium.data.local.mapper.toSummary
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryFilter
import app.otium.domain.repo.BookRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomBookRepository @Inject constructor(private val dao: BookDao) : BookRepository {

    override fun observeLibrary(filter: LibraryFilter): Flow<List<BookSummary>> =
        dao.observeLibrary(LibraryQueryBuilder.build(filter).toSupportQuery())
            .map { rows -> rows.map { it.toSummary() } }

    override fun observeBook(id: Long): Flow<Book?> = combine(
        dao.observeBookEntity(id),
        dao.observeAuthorNames(id),
        dao.observeCategoryNames(id),
        dao.observeTagNames(id),
    ) { entity, authors, categories, tags ->
        entity?.toDomain(authors, categories, tags)
    }

    override suspend fun getBook(id: Long): Try<Book> = guard {
        val entity = dao.getBookEntity(id) ?: return@guard Try.Failure(AppError.NotFound)
        Try.Success(entity.toDomain(dao.authorNames(id), dao.categoryNames(id), dao.tagNames(id)))
    }

    override suspend fun findByIsbn13(isbn13: String): Try<Book?> = guard {
        val entity = dao.findEntityByIsbn13(isbn13) ?: return@guard Try.Success(null)
        Try.Success(
            entity.toDomain(
                dao.authorNames(entity.id),
                dao.categoryNames(entity.id),
                dao.tagNames(entity.id),
            ),
        )
    }

    override suspend fun add(book: Book): Try<Long> = try {
        Try.Success(dao.insertBook(book.toEntity(), book.authors, book.categories, book.tags))
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteConstraintException) {
        val existing = book.isbn13?.let { findExistingOrNull(it) }
        if (existing != null) {
            Try.Failure(AppError.Duplicate(existing.id))
        } else {
            Try.Failure(AppError.Storage(e.message ?: "constraint violation"))
        }
    } catch (e: SQLiteException) {
        Try.Failure(AppError.Storage(e.message ?: "database error"))
    }

    override suspend fun update(book: Book): Try<Unit> = guard {
        if (dao.updateBook(book.toEntity(), book.authors, book.categories, book.tags)) {
            Try.Success(Unit)
        } else {
            Try.Failure(AppError.NotFound)
        }
    }

    override suspend fun updateProgress(id: Long, currentPage: Int, updatedAt: Long): Try<Unit> =
        guard {
            if (dao.updateProgress(id, currentPage, updatedAt) > 0) {
                Try.Success(Unit)
            } else {
                Try.Failure(AppError.NotFound)
            }
        }

    override suspend fun delete(id: Long): Try<Unit> = guard {
        if (dao.deleteBook(id)) Try.Success(Unit) else Try.Failure(AppError.NotFound)
    }

    override fun observeCategories(): Flow<List<String>> = dao.observeAllCategories()

    override fun observeTags(): Flow<List<String>> = dao.observeAllTags()

    override fun observeAuthors(): Flow<List<String>> = dao.observeAllAuthors()

    /** Best effort lookup used while reporting a constraint failure; never throws. */
    @Suppress("SwallowedException") // The original constraint failure is already being reported.
    private suspend fun findExistingOrNull(isbn13: String) = try {
        dao.findEntityByIsbn13(isbn13)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteException) {
        null
    }

    /** Converts database exceptions to [AppError.Storage]; cancellation is rethrown. */
    private inline fun <T> guard(block: () -> Try<T>): Try<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteException) {
        Try.Failure(AppError.Storage(e.message ?: "database error"))
    }
}
