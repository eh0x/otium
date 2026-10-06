package app.otium.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Reads book barcodes with the camera. The camera itself is platform code bound to the scan
 * screen; this port is what the screen's ViewModel listens to, so tests can feed it reads.
 */
interface BarcodeScanner {
    /**
     * ISBN-13 values as they are read, already checked with
     * [app.otium.domain.usecase.IsbnFromBarcode]. Hot: a collector sees the reads made while it
     * collects.
     */
    val reads: Flow<String>
}
