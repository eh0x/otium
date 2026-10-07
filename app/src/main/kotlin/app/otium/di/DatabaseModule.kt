package app.otium.di

import android.content.Context
import androidx.room.Room
import app.otium.data.local.OtiumDatabase
import app.otium.data.local.dao.BookDao
import app.otium.data.local.dao.LibraryTransferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Room only offers a varargs API. The array is tiny and spread once at startup.
    @Suppress("SpreadOperator")
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OtiumDatabase =
        Room.databaseBuilder(context, OtiumDatabase::class.java, "otium.db")
            .addMigrations(*OtiumDatabase.migrations)
            .build()

    @Provides
    fun provideBookDao(database: OtiumDatabase): BookDao = database.bookDao()

    @Provides
    fun provideTransferDao(database: OtiumDatabase): LibraryTransferDao = database.transferDao()
}
