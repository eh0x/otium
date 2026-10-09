package app.otium.di

import app.otium.data.SystemClock
import app.otium.data.cover.CameraPhotoFiles
import app.otium.data.cover.FileCoverStore
import app.otium.data.repo.RemoteMetadataRepository
import app.otium.data.repo.RoomBookRepository
import app.otium.domain.port.CapturedPhotos
import app.otium.domain.port.Clock
import app.otium.domain.port.CoverStore
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository
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
    abstract fun bindMetadataRepository(impl: RemoteMetadataRepository): MetadataRepository

    @Binds
    @Singleton
    abstract fun bindCoverStore(impl: FileCoverStore): CoverStore

    @Binds
    @Singleton
    abstract fun bindCapturedPhotos(impl: CameraPhotoFiles): CapturedPhotos

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock
}
