package app.otium.domain.port

/**
 * Arranges for books saved without their details to be looked up later, once the device is
 * online. Scheduling again while a lookup is already planned changes nothing.
 */
fun interface LookupScheduler {
    fun schedule()
}
