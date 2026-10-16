package app.otium.di

import android.content.Context
import app.otium.auth.DriveConsent
import app.otium.data.backup.GoogleAuthManager
import app.otium.data.backup.RoomLibraryRestorer
import app.otium.data.backup.ZipBackupArchiver
import app.otium.data.backup.crypto.KeystoreBackupEncryption
import app.otium.data.backup.drive.AccessTokenProvider
import app.otium.data.backup.drive.DRIVE_BASE_URL
import app.otium.data.backup.drive.DriveApi
import app.otium.data.backup.drive.GoogleDriveBackupClient
import app.otium.data.local.OtiumDatabase
import app.otium.data.remote.RemoteClients
import app.otium.domain.port.BackupArchiver
import app.otium.domain.port.BackupEncryption
import app.otium.domain.port.BackupPreferences
import app.otium.domain.port.BackupScheduler
import app.otium.domain.port.DispatcherProvider
import app.otium.domain.port.DriveAuth
import app.otium.domain.port.DriveBackupClient
import app.otium.domain.port.LibraryRestorer
import app.otium.domain.port.LookupScheduler
import app.otium.domain.usecase.BackupLock
import app.otium.domain.usecase.ConfigureAutoBackup
import app.otium.domain.usecase.FindBackups
import app.otium.domain.usecase.LibraryRestore
import app.otium.domain.usecase.RunBackup
import app.otium.domain.usecase.RunScheduledBackup
import app.otium.domain.usecase.SetBackupPassphrase
import app.otium.domain.usecase.SyncLoanReminders
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Backup and restore: the Drive client, the archive and restore pieces, the Google authorization
 * behind three interfaces, and the use cases that tie them together. One [BackupLock] is shared,
 * so a backup, a scheduled backup and a restore never run at once.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackupModule {

    @Binds
    abstract fun bindArchiver(impl: ZipBackupArchiver): BackupArchiver

    @Binds
    abstract fun bindRestorer(impl: RoomLibraryRestorer): LibraryRestorer

    @Binds
    abstract fun bindEncryption(impl: KeystoreBackupEncryption): BackupEncryption

    @Binds
    abstract fun bindTokens(impl: GoogleAuthManager): AccessTokenProvider

    @Binds
    abstract fun bindDriveAuth(impl: GoogleAuthManager): DriveAuth

    @Binds
    abstract fun bindDriveConsent(impl: GoogleAuthManager): DriveConsent

    companion object {
        @Provides
        @Singleton
        fun provideDriveApi(client: OkHttpClient): DriveApi = RemoteClients.retrofit(
            DRIVE_BASE_URL.toHttpUrl(),
            RemoteClients.transferClient(client),
        ).create(DriveApi::class.java)

        @Provides
        @Singleton
        fun provideDriveBackupClient(
            api: DriveApi,
            tokens: AccessTokenProvider,
            @ApplicationContext context: Context,
            dispatchers: DispatcherProvider,
        ): DriveBackupClient = GoogleDriveBackupClient(api, tokens, context.cacheDir, dispatchers)

        @Provides
        @Singleton
        fun provideBackupLock() = BackupLock()

        @Provides
        fun provideRunBackup(
            drive: DriveBackupClient,
            archiver: BackupArchiver,
            encryption: BackupEncryption,
            preferences: BackupPreferences,
            lock: BackupLock,
        ) = RunBackup(drive, archiver, encryption, preferences, lock)

        @Provides
        fun provideRunScheduledBackup(runBackup: RunBackup, preferences: BackupPreferences) =
            RunScheduledBackup(runBackup, preferences)

        /** Not a singleton: each screen holds its own prepared backup. The lock is shared. */
        @Provides
        @Suppress("LongParameterList") // One argument per port LibraryRestore coordinates.
        fun provideLibraryRestore(
            drive: DriveBackupClient,
            archiver: BackupArchiver,
            restorer: LibraryRestorer,
            encryption: BackupEncryption,
            lookups: LookupScheduler,
            loanReminders: SyncLoanReminders,
            lock: BackupLock,
        ) = LibraryRestore(
            drive,
            archiver,
            restorer,
            encryption,
            lookups,
            loanReminders,
            lock,
            OtiumDatabase.VERSION,
        )

        @Provides
        fun provideSetBackupPassphrase(encryption: BackupEncryption) =
            SetBackupPassphrase(encryption)

        @Provides
        fun provideFindBackups(drive: DriveBackupClient) = FindBackups(drive)

        @Provides
        fun provideConfigureAutoBackup(
            preferences: BackupPreferences,
            scheduler: BackupScheduler,
        ) = ConfigureAutoBackup(preferences, scheduler)
    }
}
