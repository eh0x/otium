package app.otium.domain.port

private const val MILLIS_PER_DAY = 86_400_000L

/** Source of the current time, injected so tests control timestamps. */
fun interface Clock {
    fun nowMillis(): Long

    /**
     * Today as an epoch day, in the phone's own time zone. This default counts days in UTC; the
     * app's clock overrides it.
     */
    fun todayEpochDay(): Long = Math.floorDiv(nowMillis(), MILLIS_PER_DAY)
}
