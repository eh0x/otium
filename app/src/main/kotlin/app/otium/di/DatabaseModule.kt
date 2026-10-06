package app.otium.di

import android.content.Context
import androidx.room.Room
import app.otium.data.local.OtiumDatabase
import app.otium.data.local.dao.BookDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Phase 6 replaces this with a swappable holder so restore can close and reopen Room.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OtiumDatabase =
        Room.databaseBuilder(context, OtiumDatabase::class.java, "otium.db")
            .addMigrations(*OtiumDatabase.migrations)
            .build()

    @Provides
    fun provideBookDao(database: OtiumDatabase): BookDao = database.bookDao()
}
