package app.otium.ui.add.confirm

import androidx.compose.runtime.Immutable
import app.otium.domain.error.AppError
import app.otium.domain.model.Book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.model.CoverChoice

/**
 * The confirm form as the user types it. Numbers stay text until saving, so a half-typed value
 * is never lost; authors and categories are typed as comma-separated lists.
 */
@Immutable
data class BookForm(
    val isbn: String = "",
    val title: String = "",
    val subtitle: String = "",
    val authors: String = "",
    val publisher: String = "",
    val publishedYear: String = "",
    val language: String = "",
    val totalPages: String = "",
    val categories: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
) {
    /** The form as a candidate. Text that is not a number counts as unknown. */
    fun toCandidate(source: BookSource, isbn10: String?) = BookCandidate(
        isbn13 = isbn.trim().takeIf(String::isNotEmpty),
        isbn10 = isbn10,
        title = title.trim(),
        subtitle = subtitle.orNull(),
        authors = authors.splitList(),
        publisher = publisher.orNull(),
        publishedYear = publishedYear.trim().toIntOrNull(),
        language = language.orNull(),
        description = description.orNull(),
        totalPages = totalPages.trim().toIntOrNull()?.takeIf { it > 0 },
        categories = categories.splitList(),
        tags = tags,
        source = source,
    )

    companion object {
        fun from(candidate: BookCandidate) = BookForm(
            isbn = candidate.isbn13 ?: candidate.isbn10.orEmpty(),
            title = candidate.title,
            subtitle = candidate.subtitle.orEmpty(),
            authors = candidate.authors.joinToString(LIST_SEPARATOR),
            publisher = candidate.publisher.orEmpty(),
            publishedYear = candidate.publishedYear?.toString().orEmpty(),
            language = candidate.language.orEmpty(),
            totalPages = candidate.totalPages?.toString().orEmpty(),
            categories = candidate.categories.joinToString(LIST_SEPARATOR),
            description = candidate.description.orEmpty(),
            tags = candidate.tags,
        )

        fun from(book: Book) = BookForm(
            isbn = book.isbn13 ?: book.isbn10.orEmpty(),
            title = book.title,
            subtitle = book.subtitle.orEmpty(),
            authors = book.authors.joinToString(LIST_SEPARATOR),
            publisher = book.publisher.orEmpty(),
            publishedYear = book.publishedYear?.toString().orEmpty(),
            language = book.language.orEmpty(),
            totalPages = book.totalPages.takeIf { it > 0 }?.toString().orEmpty(),
            categories = book.categories.joinToString(LIST_SEPARATOR),
            description = book.description.orEmpty(),
            tags = book.tags,
        )

        private const val LIST_SEPARATOR = ", "

        private fun String.orNull(): String? = trim().takeIf(String::isNotEmpty)

        private fun String.splitList(): List<String> =
            split(',').map(String::trim).filter(String::isNotEmpty)
    }
}

/**
 * Everything the confirm/edit screen shows. Outcomes are state the screen reports handled:
 * [savedBookId] tells it to leave for the saved book, [duplicateOf] to offer the book that
 * already has this ISBN, [error] to show a message until it is dismissed.
 */
@Immutable
data class ConfirmUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val form: BookForm = BookForm(),
    val cover: CoverChoice = CoverChoice.None,
    /** The cover a book being edited already has, shown while [cover] is `Unchanged`. */
    val currentCoverPath: String? = null,
    /** Covers the services offer for this book, to choose from. */
    val remoteCovers: List<String> = emptyList(),
    /** Every tag in the library, for suggestions while typing a tag. */
    val knownTags: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val savedBookId: Long? = null,
    val duplicateOf: Long? = null,
    val error: AppError? = null,
) {
    val canSave: Boolean
        get() = form.title.isNotBlank() && !isLoading && !notFound && !isSaving &&
            savedBookId == null

    /** True while the page count is missing, so the form can ask for it. */
    val pagesUnknown: Boolean get() = (form.totalPages.trim().toIntOrNull() ?: 0) <= 0
}
