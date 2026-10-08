package app.otium.ui.add.scan

import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.otium.di.ScannerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.Executors

/**
 * The live camera picture. The camera is bound to this composable's lifecycle, so it stops when
 * the screen is left or the app goes to the background. Frames go to the barcode scanner only
 * while [analyzing]; once a book is read the picture stays but decoding stops.
 */
@Composable
fun CameraPreview(analyzing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzer = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ScannerEntryPoint::class.java,
        )
            .barcodeAnalyzer()
    }
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner, controller) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            executor.shutdown()
        }
    }
    DisposableEffect(analyzing, controller) {
        if (analyzing) {
            controller.setImageAnalysisAnalyzer(executor, analyzer)
        } else {
            controller.clearImageAnalysisAnalyzer()
        }
        onDispose { controller.clearImageAnalysisAnalyzer() }
    }
    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = modifier,
    )
}
