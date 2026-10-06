package app.otium.ui.debug

/**
 * Developer actions that exist only in debug builds. The debug source set provides a working
 * implementation and the release source set the no-op, so release builds contain no seeding
 * code and the UI needs no build type checks.
 */
interface DebugTools {
    val isAvailable: Boolean

    /** Fills the library with synthetic books for scroll and search checks. Returns the count. */
    suspend fun seedLibrary(): Int
}

object NoDebugTools : DebugTools {
    override val isAvailable: Boolean = false

    override suspend fun seedLibrary(): Int = 0
}
