package app.otium.data.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.otium.domain.port.BarcodeScanner
import app.otium.domain.usecase.IsbnFromBarcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Reads book barcodes from camera frames with ML Kit's on-device model. CameraX feeds it frames
 * as an [ImageAnalysis.Analyzer] while the scan screen is showing; every frame holding a book
 * barcode reports its ISBN on [reads]. Only EAN-13 is decoded, which is what books carry.
 */
@Singleton
class MlKitBarcodeScanner @Inject constructor() :
    BarcodeScanner,
    ImageAnalysis.Analyzer {

    private val client by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build(),
        )
    }

    private val found = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val reads: SharedFlow<String> = found

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val frame = image.image
        if (frame == null) {
            image.close()
            return
        }
        client.process(InputImage.fromMediaImage(frame, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                isbnIn(barcodes.map { it.rawValue })?.let(found::tryEmit)
            }
            // The frame goes back to CameraX whether or not decoding worked.
            .addOnCompleteListener { image.close() }
    }
}

/** The first value in a frame's barcodes that is a book ISBN, or null. */
internal fun isbnIn(rawValues: List<String?>): String? =
    rawValues.firstNotNullOfOrNull { raw -> raw?.let(IsbnFromBarcode::invoke) }
