package app.otium.di

import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository
import app.otium.domain.usecase.AddBookManually
import app.otium.domain.usecase.ObserveLibrary
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
    fun provideAddBookManually(repository: BookRepository, clock: Clock) =
        AddBookManually(repository, clock)
}
