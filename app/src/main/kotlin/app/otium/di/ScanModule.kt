package app.otium.di

import androidx.camera.core.ImageAnalysis
import app.otium.data.scan.MlKitBarcodeScanner
import app.otium.domain.port.BarcodeScanner
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** The frame analyzer that decodes book barcodes, as opposed to any other analyzer. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BarcodeAnalyzer

/**
 * One scanner serves both sides: the scan screen's ViewModel listens to it as a [BarcodeScanner],
 * and the camera preview feeds it frames as an analyzer.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

    @Binds
    abstract fun bindBarcodeScanner(impl: MlKitBarcodeScanner): BarcodeScanner

    @Binds
    @BarcodeAnalyzer
    abstract fun bindBarcodeAnalyzer(impl: MlKitBarcodeScanner): ImageAnalysis.Analyzer
}

/**
 * How the camera preview, a composable outside Hilt's reach, gets the analyzer. The UI layer
 * cannot depend on the data layer, so it asks for this instead of the scanner class.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScannerEntryPoint {
    @BarcodeAnalyzer
    fun barcodeAnalyzer(): ImageAnalysis.Analyzer
}
