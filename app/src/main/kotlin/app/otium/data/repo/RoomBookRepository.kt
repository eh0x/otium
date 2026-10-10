package app.otium.data.repo

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import app.otium.data.cover.CoverFiles
import app.otium.data.cover.coversDirectory
import app.otium.data.local.LibraryQueryBuilder
import app.otium.data.local.dao.BookDao
import app.otium.data.local.entity.BookEntity
import app.otium.data.local.mapper.toDomain
import app.otium.data.local.mapper.toEntity
import app.otium.data.local.mapper.toSummary
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.SortKeys
import app.otium.domain.model.SortOrder
import app.otium.domain.repo.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomBookRepository internal constructor(
    private val dao: BookDao,
    private val covers: CoverFiles,
) : BookRepository {

    @Inject
    internal constructor(
        dao: BookDao,
        @ApplicationContext context: Context,
    ) : this(dao, CoverFiles(coversDirectory(context)))

    override fun observeLibrary(filter: LibraryFilter): Flow<List<BookSummary>> =
        dao.observeLibrary(LibraryQueryBuilder.build(filter).toSupportQuery())
            .map { rows -> rows.map { it.toSummary(covers) }.sortedFor(filter.sort) }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override fun observeBook(id: Long): Flow<Book?> = combine(
        dao.observeBookEntity(id),
        dao.observeAuthorNames(id),
        dao.observeCategoryNames(id),
        dao.observeTagNames(id),
    ) { entity, authors, categories, tags ->
        entity?.toDomain(authors, categories, tags, covers)
    }

    override suspend fun getBook(id: Long): Try<Book> = guard {
        val entity = dao.getBookEntity(id) ?: return@guard Try.Failure(AppError.NotFound)
        Try.Success(dao.withLabels(entity, covers))
    }

    override suspend fun findByIsbn13(isbn13: String): Try<Book?> = guard {
        val entity = dao.findEntityByIsbn13(isbn13) ?: return@guard Try.Success(null)
        Try.Success(dao.withLabels(entity, covers))
    }

    override suspend fun getBooksNeedingLookup(): Try<List<Book>> = guard {
        Try.Success(dao.booksNeedingLookup().map { dao.withLabels(it, covers) })
    }

    override suspend fun add(book: Book): Try<Long> = try {
        Try.Success(dao.insertBook(book.toEntity(covers), book.authors, book.categories, book.tags))
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteConstraintException) {
        val existing = book.isbn13?.let { dao.findExistingOrNull(it) }
        if (existing != null) {
            Try.Failure(AppError.Duplicate(existing.id))
        } else {
            Try.Failure(AppError.Storage(e.message ?: "constraint violation"))
        }
    } catch (e: SQLiteException) {
        Try.Failure(AppError.Storage(e.message ?: "database error"))
    }

    override suspend fun update(book: Book): Try<Unit> = guard {
        if (dao.updateBook(book.toEntity(covers), book.authors, book.categories, book.tags)) {
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

    /** Converts database exceptions to [AppError.Storage]; cancellation is rethrown. */
    private inline fun <T> guard(block: () -> Try<T>): Try<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SQLiteException) {
        Try.Failure(AppError.Storage(e.message ?: "database error"))
    }
}

/** The domain book for [entity], with its authors, categories and tags in order. */
private suspend fun BookDao.withLabels(entity: BookEntity, covers: CoverFiles): Book =
    entity.toDomain(authorNames(entity.id), categoryNames(entity.id), tagNames(entity.id), covers)

/** Best effort lookup used while reporting a constraint failure; never throws. */
@Suppress("SwallowedException") // The original constraint failure is already being reported.
private suspend fun BookDao.findExistingOrNull(isbn13: String) = try {
    findEntityByIsbn13(isbn13)
} catch (e: CancellationException) {
    throw e
} catch (e: SQLiteException) {
    null
}

/**
 * Catalogue order for title and author, which SQL cannot express simply. Other orders come
 * from the query as they are. The sort is stable, so ties keep the query's order.
 */
private fun List<BookSummary>.sortedFor(sort: SortOrder): List<BookSummary> = when (sort) {
    SortOrder.TITLE -> sortedWith(SortKeys.byTitle)
    SortOrder.AUTHOR -> sortedWith(SortKeys.byAuthor)
    SortOrder.RECENTLY_ADDED, SortOrder.LAST_UPDATED, SortOrder.PROGRESS -> this
}
