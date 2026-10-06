package app.otium.domain.fakes

import app.otium.domain.port.BarcodeScanner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A camera that reads whatever the test holds up to it. */
class FakeBarcodeScanner : BarcodeScanner {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)

    override val reads: SharedFlow<String> = flow

    /** Reports [isbn13] as read, as the real scanner does after its checks. */
    fun read(isbn13: String) {
        check(flow.tryEmit(isbn13)) { "the read was dropped" }
    }
}
