package app.otium.data

import app.otium.domain.port.Clock
import javax.inject.Inject

class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
