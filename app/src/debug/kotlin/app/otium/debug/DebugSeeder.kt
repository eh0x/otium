package app.otium.debug

import app.otium.domain.error.Try
import app.otium.domain.repo.BookRepository
import javax.inject.Inject

/** Fills the library with synthetic books for scroll and search checks. Debug builds only. */
class DebugSeeder @Inject constructor(
    private val repository: BookRepository,
) {
    /** Returns how many books were added. Books whose ISBN already exists are skipped. */
    suspend fun seed(count: Int = 5_000): Int =
        SyntheticBooks.generate(count).count { repository.add(it) is Try.Success }
}
