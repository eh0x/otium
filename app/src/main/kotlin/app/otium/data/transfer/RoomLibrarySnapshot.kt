package app.otium.data.transfer

import android.content.Context
import android.database.sqlite.SQLiteException
import app.otium.data.backup.LibraryRows
import app.otium.data.backup.readAll
import app.otium.data.cover.CoverFiles
import app.otium.data.cover.coversDirectory
import app.otium.data.local.OtiumDatabase
import app.otium.data.local.mapper.toDomain
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.model.Book
import app.otium.domain.port.LibrarySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The whole library for an export, read in one transaction (as the backup reads it) and put
 * together in memory, instead of three label queries per book.
 */
class RoomLibrarySnapshot internal constructor(
    private val database: OtiumDatabase,
    private val covers: CoverFiles,
) : LibrarySnapshot {

    @Inject
    internal constructor(
        database: OtiumDatabase,
        @ApplicationContext context: Context,
    ) : this(database, CoverFiles(coversDirectory(context)))

    override suspend fun books(): Try<List<Book>> = try {
        Try.Success(database.readAll().toBooks(covers))
    } catch (error: SQLiteException) {
        Try.Failure(AppError.Storage(error.message ?: "database error"))
    }
}

/** The books in [this], oldest first, each with its labels in the order the owner gave them. */
internal fun LibraryRows.toBooks(covers: CoverFiles): List<Book> {
    val authorNames = authors.associate { it.id to it.name }
    val categoryNames = categories.associate { it.id to it.name }
    val tagNames = tags.associate { it.id to it.name }
    val authorsOf = bookAuthors.groupBy { it.bookId }
    val categoriesOf = bookCategories.groupBy { it.bookId }
    val tagsOf = bookTags.groupBy { it.bookId }
    return books.map { book ->
        book.toDomain(
            authors = authorsOf[book.id].orEmpty().sortedBy { it.position }
                .mapNotNull { authorNames[it.authorId] },
            categories = categoriesOf[book.id].orEmpty().sortedBy { it.position }
                .mapNotNull { categoryNames[it.categoryId] },
            tags = tagsOf[book.id].orEmpty().sortedBy { it.position }
                .mapNotNull { tagNames[it.tagId] },
            covers = covers,
        )
    }
}
