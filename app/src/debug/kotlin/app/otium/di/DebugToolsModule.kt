package app.otium.di

import app.otium.debug.DebugSeeder
import app.otium.debug.SeedingDebugTools
import app.otium.ui.debug.DebugTools
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug builds can seed the library with synthetic books. The release version is a no-op. */
@Module
@InstallIn(SingletonComponent::class)
object DebugToolsModule {
    @Provides
    fun provideDebugTools(seeder: DebugSeeder): DebugTools = SeedingDebugTools(seeder)
}
