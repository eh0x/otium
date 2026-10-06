package app.otium.debug

import app.otium.ui.debug.DebugTools

/** Debug build implementation of [DebugTools]: fills the library with synthetic books. */
class SeedingDebugTools(private val seeder: DebugSeeder) : DebugTools {
    override val isAvailable: Boolean = true

    override suspend fun seedLibrary(): Int = seeder.seed(SEED_COUNT)

    private companion object {
        const val SEED_COUNT = 5_000
    }
}
