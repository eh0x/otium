package app.otium.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.otium.applock.BiometricDeviceAuthenticator
import app.otium.applock.DeviceAuthenticator
import app.otium.data.backup.crypto.BackupKeyStore
import app.otium.data.prefs.BackupStateStore
import app.otium.data.prefs.DataStoreAppLockPreferences
import app.otium.data.prefs.DataStoreBackupPreferences
import app.otium.data.prefs.DataStoreLibraryPreferences
import app.otium.data.prefs.MovePreferences
import app.otium.data.prefs.isBackupState
import app.otium.domain.port.AppLockPreferences
import app.otium.domain.port.BackupPreferences
import app.otium.domain.port.LibraryPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindLibraryPreferences(impl: DataStoreLibraryPreferences): LibraryPreferences

    @Binds
    @Singleton
    abstract fun bindBackupPreferences(impl: DataStoreBackupPreferences): BackupPreferences

    @Binds
    @Singleton
    abstract fun bindAppLockPreferences(impl: DataStoreAppLockPreferences): AppLockPreferences

    @Binds
    abstract fun bindDeviceAuthenticator(impl: BiometricDeviceAuthenticator): DeviceAuthenticator

    companion object {
        @Provides
        @Singleton
        fun provideLibraryDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("library") }

        /**
         * The Drive connection and backup bookkeeping, in a file of its own: see
         * [BackupStateStore]. Installs that kept them with the library settings have them moved
         * here on first read.
         */
        @Provides
        @Singleton
        @BackupStateStore
        fun provideBackupStateStore(
            @ApplicationContext context: Context,
            library: DataStore<Preferences>,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            migrations = listOf(MovePreferences(library, ::isBackupState)),
        ) { context.preferencesDataStoreFile("backup_state") }

        /** The wrapped backup key, in a file of its own: see [BackupKeyStore]. */
        @Provides
        @Singleton
        @BackupKeyStore
        fun provideBackupKeyStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("backup_key") }
    }
}
