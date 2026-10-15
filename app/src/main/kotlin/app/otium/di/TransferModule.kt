package app.otium.di

import app.otium.data.transfer.ContentResolverLibraryFiles
import app.otium.data.transfer.JsonLibraryFormat
import app.otium.data.transfer.RoomLibrarySnapshot
import app.otium.domain.port.LibraryFiles
import app.otium.domain.port.LibraryJson
import app.otium.domain.port.LibrarySnapshot
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Library import and export: the files the owner picks, Otium's JSON, and the whole library. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {

    @Binds
    abstract fun bindLibraryFiles(impl: ContentResolverLibraryFiles): LibraryFiles

    @Binds
    abstract fun bindLibraryJson(impl: JsonLibraryFormat): LibraryJson

    @Binds
    abstract fun bindLibrarySnapshot(impl: RoomLibrarySnapshot): LibrarySnapshot
}
