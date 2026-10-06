package app.otium.di

import app.otium.data.SystemClock
import app.otium.data.repo.RoomBookRepository
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
    abstract fun bindBookRepository(impl: RoomBookRepository): BookRepository

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock
}
