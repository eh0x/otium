package app.otium.ui.add.isbn

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IsbnEntryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeBookRepository()
    private val metadata = FakeMetadataRepository()
    private val vm = IsbnEntryViewModel(
        AddBookFromIsbn(repo, metadata),
        SaveIsbnForLater(repo, FixedClock()),
    )

    private val dune =
        BookCandidate(isbn13 = "9780441013593", title = "Dune", source = BookSource.GOOGLE)

    private val state get() = vm.uiState.value

    @Test
    fun `a complete valid isbn can be looked up`() {
        vm.onInputChange("978-0-441-01359-3")

        assertTrue(state.canLookUp)
        assertNull(state.inputError)
    }

    @Test
    fun `a partial isbn is neither an error nor ready`() {
        vm.onInputChange("978044")

        assertFalse(state.canLookUp)
        assertNull(state.inputError)
    }

    @Test
    fun `a complete isbn with a wrong check digit is flagged while typing`() {
        vm.onInputChange("9780441013594")

        assertFalse(state.canLookUp)
        assertEquals(AppError.InvalidIsbn("9780441013594"), state.inputError)
    }

    @Test
    fun `a found book goes to the confirm screen`() = runTest {
        metadata.isbnResult = Try.Success(dune)
        vm.onInputChange("9780441013593")

        vm.onLookUp()
        assertTrue(state.isLooking)
        advanceUntilIdle()

        assertFalse(state.isLooking)
        assertEquals(AddDestination.Confirm(dune), state.destination)
        assertEquals(listOf("9780441013593"), metadata.lookedUp)
    }

    @Test
    fun `an isbn ten is looked up as isbn thirteen`() = runTest {
        metadata.isbnResult = Try.Success(dune)
        vm.onInputChange("0441013597")

        vm.onLookUp()
        advanceUntilIdle()

        assertEquals(listOf("9780441013593"), metadata.lookedUp)
    }

    @Test
    fun `a book already owned opens instead`() = runTest {
        val owned = repo.seed(book(isbn13 = "9780441013593"))
        vm.onInputChange("9780441013593")

        vm.onLookUp()
        advanceUntilIdle()

        assertEquals(AddDestination.Book(owned.id), state.destination)
        assertTrue(metadata.lookedUp.isEmpty())
    }

    @Test
    fun `a book nobody knows opens an empty form with the isbn`() = runTest {
        metadata.isbnResult = Try.Failure(AppError.NotFound)
        vm.onInputChange("9780441013593")

        vm.onLookUp()
        advanceUntilIdle()

        val destination = state.destination as AddDestination.Confirm
        assertEquals("9780441013593", destination.candidate.isbn13)
        assertEquals("", destination.candidate.title)
    }

    @Test
    fun `offline offers to keep the isbn for later`() = runTest {
        metadata.isbnResult = Try.Failure(AppError.Offline)
        vm.onInputChange("9780441013593")

        vm.onLookUp()
        advanceUntilIdle()

        assertEquals(AppError.Offline, state.lookupError)
        assertTrue(state.canSaveForLater)
        assertNull(state.destination)
    }

    @Test
    fun `an isbn kept for later is saved and opened`() = runTest {
        metadata.isbnResult = Try.Failure(AppError.Offline)
        vm.onInputChange("9780441013593")
        vm.onLookUp()
        advanceUntilIdle()

        vm.onSaveForLater()
        advanceUntilIdle()

        val saved = repo.all.single()
        assertTrue(saved.needsLookup)
        assertEquals(AddDestination.Book(saved.id), state.destination)
    }

    @Test
    fun `details can be typed in by hand after a failed lookup`() = runTest {
        metadata.isbnResult = Try.Failure(AppError.RateLimited)
        vm.onInputChange("9780441013593")
        vm.onLookUp()
        advanceUntilIdle()

        vm.onEnterManually()

        val destination = state.destination as AddDestination.Confirm
        assertEquals("9780441013593", destination.candidate.isbn13)
        assertEquals(BookSource.MANUAL, destination.candidate.source)
    }

    @Test
    fun `changing the isbn clears the last error and navigating clears the destination`() =
        runTest {
            metadata.isbnResult = Try.Failure(AppError.Offline)
            vm.onInputChange("9780441013593")
            vm.onLookUp()
            advanceUntilIdle()

            vm.onInputChange("978044101359")
            assertNull(state.lookupError)

            vm.onEnterManually()
            vm.onNavigated()
            assertNull(state.destination)
        }
}
