package app.otium.domain.fakes

import app.otium.domain.model.LibraryViewMode
import app.otium.domain.port.LibraryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLibraryPreferences(initial: LibraryViewMode = LibraryViewMode.SHELF) :
    LibraryPreferences {
    private val mode = MutableStateFlow(initial)

    override val viewMode: Flow<LibraryViewMode> = mode

    override suspend fun setViewMode(mode: LibraryViewMode) {
        this.mode.value = mode
    }
}
