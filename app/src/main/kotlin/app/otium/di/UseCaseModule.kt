package app.otium.di

import app.otium.domain.port.Clock
import app.otium.domain.port.CoverStore
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.port.LibraryFiles
import app.otium.domain.port.LibraryJson
import app.otium.domain.port.LibrarySnapshot
import app.otium.domain.port.LookupScheduler
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository
import app.otium.domain.repo.NoteRepository
import app.otium.domain.usecase.AddBookFromIsbn
import app.otium.domain.usecase.BackupLock
import app.otium.domain.usecase.ExportLibrary
import app.otium.domain.usecase.ImportLibrary
import app.otium.domain.usecase.ReadLibraryFile
import app.otium.domain.usecase.ReadingTracker
import app.otium.domain.usecase.RetryPendingLookups
import app.otium.domain.usecase.SaveBook
import app.otium.domain.usecase.SaveIsbnForLater
import app.otium.domain.usecase.SaveNote
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** :domain has no DI dependency, so use cases are constructed here. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideReadingTracker(repository: BookRepository, clock: Clock) =
        ReadingTracker(repository, clock)

    @Provides
    fun provideAddBookFromIsbn(books: BookRepository, metadata: MetadataRepository) =
        AddBookFromIsbn(books, metadata)

    @Provides
    fun provideSaveBook(repository: BookRepository, covers: CoverStore, clock: Clock) =
        SaveBook(repository, covers, clock)

    @Provides
    fun provideSaveNote(notes: NoteRepository, clock: Clock) = SaveNote(notes, clock)

    @Provides
    fun provideSaveIsbnForLater(
        repository: BookRepository,
        clock: Clock,
        scheduler: LookupScheduler,
    ) = SaveIsbnForLater(repository, clock, scheduler)

    @Provides
    fun provideReadLibraryFile(
        files: LibraryFiles,
        json: LibraryJson,
        dispatchers: DispatcherProvider,
    ) = ReadLibraryFile(files, json, dispatchers.default)

    @Provides
    fun provideImportLibrary(
        books: BookRepository,
        readFile: ReadLibraryFile,
        clock: Clock,
        scheduler: LookupScheduler,
        lock: BackupLock,
    ) = ImportLibrary(books, readFile, clock, scheduler, lock)

    @Provides
    fun provideExportLibrary(
        snapshot: LibrarySnapshot,
        files: LibraryFiles,
        json: LibraryJson,
        clock: Clock,
        dispatchers: DispatcherProvider,
    ) = ExportLibrary(snapshot, files, json, clock, dispatchers.default)

    @Provides
    fun provideRetryPendingLookups(
        books: BookRepository,
        metadata: MetadataRepository,
        covers: CoverStore,
        clock: Clock,
    ) = RetryPendingLookups(books, metadata, covers, clock)
}
