package app.otium.testing

import app.otium.ui.debug.DebugTools

class FakeDebugTools(override val isAvailable: Boolean = true) : DebugTools {
    var seedCalls = 0
        private set

    override suspend fun seedLibrary(): Int {
        seedCalls++
        return SEEDED_COUNT
    }

    companion object {
        const val SEEDED_COUNT = 5_000
    }
}
