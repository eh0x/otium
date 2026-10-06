package app.otium.di

import app.otium.domain.port.Clock
import app.otium.domain.port.CoverStore
import app.otium.domain.port.LookupScheduler
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository
import app.otium.domain.usecase.AddBookFromIsbn
import app.otium.domain.usecase.MarkFinished
import app.otium.domain.usecase.ObserveLibrary
import app.otium.domain.usecase.RetryPendingLookups
import app.otium.domain.usecase.SaveBook
import app.otium.domain.usecase.SaveIsbnForLater
import app.otium.domain.usecase.SetTotalPages
import app.otium.domain.usecase.UpdateProgress
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** :domain has no DI dependency, so use cases are constructed here. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideObserveLibrary(repository: BookRepository) = ObserveLibrary(repository)

    @Provides
    fun provideUpdateProgress(repository: BookRepository, clock: Clock) =
        UpdateProgress(repository, clock)

    @Provides
    fun provideMarkFinished(repository: BookRepository, updateProgress: UpdateProgress) =
        MarkFinished(repository, updateProgress)

    @Provides
    fun provideSetTotalPages(repository: BookRepository, clock: Clock) =
        SetTotalPages(repository, clock)

    @Provides
    fun provideAddBookFromIsbn(books: BookRepository, metadata: MetadataRepository) =
        AddBookFromIsbn(books, metadata)

    @Provides
    fun provideSaveBook(repository: BookRepository, covers: CoverStore, clock: Clock) =
        SaveBook(repository, covers, clock)

    @Provides
    fun provideSaveIsbnForLater(
        repository: BookRepository,
        clock: Clock,
        scheduler: LookupScheduler,
    ) = SaveIsbnForLater(repository, clock, scheduler)

    @Provides
    fun provideRetryPendingLookups(
        books: BookRepository,
        metadata: MetadataRepository,
        covers: CoverStore,
        clock: Clock,
    ) = RetryPendingLookups(books, metadata, covers, clock)
}
