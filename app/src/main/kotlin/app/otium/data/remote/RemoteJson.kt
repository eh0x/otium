package app.otium.data.remote

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration for every remote API. Remote data is not under our control, so it is
 * read generously: unknown keys are skipped, quoted and bare numbers are both accepted, and a
 * field of the wrong type becomes "missing" (see [LenientSerializer]) instead of failing the
 * whole record.
 */
object RemoteJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    inline fun <reified T> decodeFromString(text: String): T = json.decodeFromString(text)
}
