package app.otium.domain.usecase

import app.otium.domain.error.Try
import app.otium.domain.model.LoanReminder
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.LoanRepository

/**
 * Works out what to say when a loan's reminder falls due, or that there is nothing to say: the
 * loan was returned, deleted or had its reminder cleared or moved to a later day since the
 * reminder was planned. A reminder is said once and never turns into "overdue"; the work that
 * planned it is gone once it has run. A loan or book that cannot be read ends quietly too, as the
 * reminder is only a courtesy.
 */
class PrepareLoanReminder(
    private val loans: LoanRepository,
    private val books: BookRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(loanId: Long): LoanReminder? {
        val loan = (loans.getLoan(loanId) as? Try.Success)?.value ?: return null
        val remindOn = loan.remindOn
        if (!loan.isOpen || remindOn == null || remindOn > clock.todayEpochDay()) return null
        val book = (books.getBook(loan.bookId) as? Try.Success)?.value ?: return null
        return LoanReminder(loan.id, book.title, loan.counterparty, loan.direction, loan.lentOn)
    }
}
