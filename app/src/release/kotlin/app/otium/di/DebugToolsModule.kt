package app.otium.di

import app.otium.ui.debug.DebugTools
import app.otium.ui.debug.NoDebugTools
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release builds carry no developer tools. The debug source set has its own version. */
@Module
@InstallIn(SingletonComponent::class)
object DebugToolsModule {
    @Provides
    fun provideDebugTools(): DebugTools = NoDebugTools
}
