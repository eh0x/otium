package app.otium.di

import app.otium.data.SystemClock
import app.otium.data.repo.BookRepositoryImpl
import app.otium.domain.port.Clock
import app.otium.domain.repo.BookRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock
}
