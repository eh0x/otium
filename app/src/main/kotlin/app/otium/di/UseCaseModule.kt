package app.otium.di

import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository
import app.otium.domain.usecase.AddBookFromIsbn
import app.otium.domain.usecase.AddBookManually
import app.otium.domain.usecase.MarkFinished
import app.otium.domain.usecase.ObserveLibrary
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
    fun provideAddBookManually(repository: BookRepository, clock: Clock) =
        AddBookManually(repository, clock)
}
