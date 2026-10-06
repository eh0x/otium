package app.otium.testing

/** Reads a recorded API response from `src/test/resources/fixtures`. See the README there. */
object Fixtures {
    fun read(path: String): String {
        val stream = checkNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$path")) {
            "Missing fixture: fixtures/$path"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}
