package app.otium.domain.port

/** Source of the current time, injected so tests control timestamps. */
fun interface Clock {
    fun nowMillis(): Long
}
