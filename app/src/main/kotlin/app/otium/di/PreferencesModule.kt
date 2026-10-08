package app.otium.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.otium.data.prefs.DataStoreBackupPreferences
import app.otium.data.prefs.DataStoreLibraryPreferences
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

    companion object {
        @Provides
        @Singleton
        fun provideLibraryDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("library") }
    }
}
