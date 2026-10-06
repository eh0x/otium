package app.otium.domain.port

import app.otium.domain.model.LibraryViewMode
import kotlinx.coroutines.flow.Flow

/** Per-device presentation choices for the library. Not part of the backup. */
interface LibraryPreferences {
    val viewMode: Flow<LibraryViewMode>

    suspend fun setViewMode(mode: LibraryViewMode)
}
