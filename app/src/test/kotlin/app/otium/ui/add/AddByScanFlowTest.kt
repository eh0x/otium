package app.otium.ui.add

import androidx.camera.core.ImageAnalysis
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.otium.MainActivity
import app.otium.di.BarcodeAnalyzer
import app.otium.di.RepositoryModule
import app.otium.di.ScanModule
import app.otium.di.WorkModule
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBackupScheduler
import app.otium.domain.fakes.FakeBarcodeScanner
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeCoverStore
import app.otium.domain.fakes.FakeMetadataRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.port.BackupScheduler
import app.otium.domain.port.BarcodeScanner
import app.otium.domain.port.CapturedPhotos
import app.otium.domain.port.Clock
import app.otium.domain.port.CoverStore
import app.otium.domain.port.LookupScheduler
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scan path end to end with a fake camera: a read barcode is looked up, confirmed and
 * saved. Robolectric has no camera, so the screen shows its no-camera face while the fake
 * scanner still delivers reads to the ViewModel; the camera itself is checked on a device.
 */
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, WorkModule::class, ScanModule::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h740dp")
class AddByScanFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val library = FakeBookRepository()
    private val camera = FakeBarcodeScanner()

    @BindValue @JvmField
    val books: BookRepository = library

    @BindValue @JvmField
    val metadata: MetadataRepository = FakeMetadataRepository().apply {
        isbnResult = Try.Success(
            BookCandidate(
                isbn13 = DUNE,
                title = "Dune",
                totalPages = 412,
                source = BookSource.GOOGLE,
            ),
        )
    }

    @BindValue @JvmField
    val covers: CoverStore = FakeCoverStore()

    @BindValue @JvmField
    val clock: Clock = FixedClock()

    @BindValue @JvmField
    val capturedPhotos: CapturedPhotos = CapturedPhotos {}

    @BindValue @JvmField
    val scheduler: LookupScheduler = LookupScheduler {}

    @BindValue @JvmField
    val backupScheduler: BackupScheduler = FakeBackupScheduler()

    @BindValue @JvmField
    val scanner: BarcodeScanner = camera

    @BindValue @JvmField
    @BarcodeAnalyzer
    val analyzer: ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { it.close() }

    private fun waitFor(text: String) = rule.waitUntil(TIMEOUT) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `a scanned barcode is looked up, confirmed and saved`() {
        rule.onNodeWithContentDescription("Add a book").performClick()
        rule.onNodeWithText("Scan the barcode").performClick()
        waitFor("Enter the ISBN instead")

        camera.read(DUNE)

        waitFor("Check the details")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(TIMEOUT) { library.all.isNotEmpty() }
        assertEquals("Dune", library.all.single().title)
    }

    @Test
    fun `the isbn can be typed instead of scanned`() {
        rule.onNodeWithContentDescription("Add a book").performClick()
        rule.onNodeWithText("Scan the barcode").performClick()
        waitFor("Enter the ISBN instead")

        rule.onNodeWithText("Enter the ISBN instead").performClick()

        waitFor("Look up")
    }

    private companion object {
        const val DUNE = "9780441013593"
        const val TIMEOUT = 5_000L
    }
}
