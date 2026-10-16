package app.otium.di

import app.otium.domain.port.Clock
import app.otium.domain.port.LoanReminderScheduler
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.LoanRepository
import app.otium.domain.usecase.LendingLedger
import app.otium.domain.usecase.PrepareLoanReminder
import app.otium.domain.usecase.SyncLoanReminders
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The lending ledger's use cases, apart from [UseCaseModule]. :domain has no DI dependency. */
@Module
@InstallIn(SingletonComponent::class)
object LendingModule {

    @Provides
    fun provideLendingLedger(
        loans: LoanRepository,
        reminders: LoanReminderScheduler,
        clock: Clock,
    ) = LendingLedger(loans, reminders, clock)

    @Provides
    fun provideSyncLoanReminders(
        loans: LoanRepository,
        reminders: LoanReminderScheduler,
        clock: Clock,
    ) = SyncLoanReminders(loans, reminders, clock)

    @Provides
    fun providePrepareLoanReminder(loans: LoanRepository, books: BookRepository, clock: Clock) =
        PrepareLoanReminder(loans, books, clock)
}
