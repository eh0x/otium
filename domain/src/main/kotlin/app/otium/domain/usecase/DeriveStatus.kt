package app.otium.domain.usecase

import app.otium.domain.model.BookStatus

/**
 * Status is derived from the page numbers, never stored. An unknown total (0 or less)
 * is always "not started". This must stay in sync with the status SQL in the library DAO.
 */
object DeriveStatus {
    operator fun invoke(currentPage: Int, totalPages: Int): BookStatus = when {
        currentPage <= 0 || totalPages <= 0 -> BookStatus.NOT_STARTED
        currentPage >= totalPages -> BookStatus.READ
        else -> BookStatus.IN_PROGRESS
    }
}
