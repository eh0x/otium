package app.otium.domain.usecase

import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryFilter
import app.otium.domain.repo.BookRepository
import kotlinx.coroutines.flow.Flow

/** Observes the library for a filter, normalizing the search text first. */
class ObserveLibrary(private val repository: BookRepository) {
    operator fun invoke(filter: LibraryFilter): Flow<List<BookSummary>> =
        repository.observeLibrary(filter.copy(query = filter.query.trim().replace(WHITESPACE, " ")))

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
