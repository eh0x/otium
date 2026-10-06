package app.otium.ui.add.scan

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBarcodeScanner
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeMetadataRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.usecase.AddBookFromIsbn
import app.otium.domain.usecase.SaveIsbnForLater
import app.otium.testing.MainDispatcherRule
import app.otium.ui.add.AddDestination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeBookRepository()
    private val metadata = FakeMetadataRepository()
    private val camera = FakeBarcodeScanner()
    private var lookupsPlanned = 0

    private val dune = BookCandidate(isbn13 = DUNE, title = "Dune", source = BookSource.GOOGLE)

    private fun TestScope.scanning(): ScanViewModel = ScanViewModel(
        camera,
        AddBookFromIsbn(repo, metadata),
        SaveIsbnForLater(repo, FixedClock()) { lookupsPlanned++ },
    ).also { advanceUntilIdle() }

    private fun TestScope.read(vm: ScanViewModel, isbn13: String = DUNE): ScanUiState {
        camera.read(isbn13)
        advanceUntilIdle()
        return vm.uiState.value
    }

    @Test
    fun `the camera starts out looking for a barcode`() = runTest {
        val vm = scanning()

        assertEquals(ScanPhase.SCANNING, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.reads)
    }

    @Test
    fun `a read book goes to the confirm screen`() = runTest {
        metadata.isbnResult = Try.Success(dune)
        val vm = scanning()

        val state = read(vm)

        assertEquals(1, state.reads)
        assertEquals(DUNE, state.isbn13)
        assertEquals(AddDestination.Confirm(dune), state.destination)
        assertEquals(listOf(DUNE), metadata.lookedUp)
    }

    @Test
    fun `a book already owned opens instead`() = runTest {
        val owned = repo.seed(book(isbn13 = DUNE))
        val vm = scanning()

        assertEquals(AddDestination.Book(owned.id), read(vm).destination)
    }

    @Test
    fun `reads after the first are ignored while that one is handled`() = runTest {
        metadata.isbnResult = Try.Failure(AppError.Offline)
        val vm = scanning()

        read(vm)
        val state = read(vm, "9780141439587")

        assertEquals(1, state.reads)
        assertEquals(DUNE, state.isbn13)
        assertEquals(listOf(DUNE), metadata.lookedUp)
    }

    @Test
    fun `a failed lookup offers the ways out and scanning can start again`() = runTest {
        metadata.isbnResult = Try.Failure(AppError.Offline)
        val vm = scanning()

        val failed = read(vm)
        assertEquals(ScanPhase.FAILED, failed.phase)
        assertEquals(AppError.Offline, failed.error)
        assertTrue(failed.canSaveForLater)

        vm.onScanAgain()
        assertEquals(ScanPhase.SCANNING, vm.uiState.value.phase)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `a read isbn can be kept for later`() = runTest {
        metadata.isbnResult = Try.Failure(AppError.Timeout)
        val vm = scanning()
        read(vm)

        vm.onSaveForLater()
        advanceUntilIdle()

        val saved = repo.all.single()
        assertTrue(saved.needsLookup)
        assertEquals(AddDestination.Book(saved.id), vm.uiState.value.destination)
        assertEquals(1, lookupsPlanned)
    }

    @Test
    fun `the details can be typed with the read isbn filled in`() = runTest {
        metadata.isbnResult = Try.Failure(AppError.RateLimited)
        val vm = scanning()
        read(vm)

        vm.onEnterManually()

        val destination = vm.uiState.value.destination as AddDestination.Confirm
        assertEquals(DUNE, destination.candidate.isbn13)
    }

    @Test
    fun `coming back from the next screen scans again`() = runTest {
        metadata.isbnResult = Try.Success(dune)
        val vm = scanning()
        read(vm)

        vm.onNavigated()

        val state = vm.uiState.value
        assertNull(state.destination)
        assertEquals(ScanPhase.SCANNING, state.phase)
        assertEquals(2, read(vm).reads)
    }

    private companion object {
        const val DUNE = "9780441013593"
    }
}
