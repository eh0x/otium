package app.otium.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.otium.domain.error.AppError
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.usecase.MarkFinished
import app.otium.domain.usecase.SetTotalPages
import app.otium.domain.usecase.UpdateProgress
import app.otium.testing.MainDispatcherRule
import app.otium.ui.navigation.DetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric because reading the route arguments goes through an Android Bundle. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeBookRepository()
    private val clock = FixedClock(now = 5_000L)

    private fun viewModel(id: Long) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("bookId" to id)),
        repository = repo,
        updateProgress = UpdateProgress(repo, clock),
        markFinished = MarkFinished(repo, UpdateProgress(repo, clock)),
        setTotalPages = SetTotalPages(repo, clock),
    )

    private fun TestScope.screenOpen(vm: DetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    @Test
    fun `the route arguments name the book`() {
        assertEquals(7L, SavedStateHandle(mapOf("bookId" to 7L)).toRoute<DetailRoute>().bookId)
    }

    // --- Loading ---

    @Test
    fun `starts loading then shows the book`() = runTest {
        val stored = repo.seed(book(title = "Dune"))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        assertTrue(vm.uiState.value.isLoading)

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Dune", vm.uiState.value.book?.title)
    }

    @Test
    fun `a book that does not exist is reported as not found`() = runTest {
        val vm = viewModel(99L)
        screenOpen(vm)

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.book)
    }

    // --- Progress ---

    @Test
    fun `confirming a page stores it and the screen follows`() = runTest {
        val stored = repo.seed(book(totalPages = 300))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onProgressConfirmed(120)
        advanceUntilIdle()

        assertEquals(120, vm.uiState.value.book?.currentPage)
        assertEquals(Triple(stored.id, 120, 5_000L), repo.lastProgressUpdate)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `a page past the end is stored as the last page`() = runTest {
        val stored = repo.seed(book(totalPages = 300))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onProgressConfirmed(9_999)
        advanceUntilIdle()

        assertEquals(300, vm.uiState.value.book?.currentPage)
    }

    @Test
    fun `mark finished goes to the last page`() = runTest {
        val stored = repo.seed(book(totalPages = 250, currentPage = 10))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onMarkFinished()
        advanceUntilIdle()

        assertEquals(250, vm.uiState.value.book?.currentPage)
    }

    @Test
    fun `mark finished does nothing while the total is unknown`() = runTest {
        val stored = repo.seed(book(totalPages = 0))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onMarkFinished()
        advanceUntilIdle()

        assertNull(repo.lastProgressUpdate)
    }

    // --- Total pages ---

    @Test
    fun `setting the total stores it`() = runTest {
        val stored = repo.seed(book(totalPages = 0))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onTotalPagesConfirmed(412)
        advanceUntilIdle()

        assertEquals(412, vm.uiState.value.book?.totalPages)
    }

    @Test
    fun `an invalid total is reported and nothing changes`() = runTest {
        val stored = repo.seed(book(totalPages = 0))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onTotalPagesConfirmed(0)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error is AppError.InvalidInput)
        assertEquals(0, vm.uiState.value.book?.totalPages)
    }

    // --- Errors ---

    @Test
    fun `a failed save is reported and can be dismissed`() = runTest {
        val stored = repo.seed(book(totalPages = 300))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()
        repo.failWith = AppError.Storage("disk full")

        vm.onProgressConfirmed(50)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error is AppError.Storage)
        assertEquals(0, vm.uiState.value.book?.currentPage)

        vm.onErrorShown()
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
    }

    // --- Delete ---

    @Test
    fun `deleting removes the book and tells the screen to leave`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onDelete()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.deleted)
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `a failed delete keeps the book and reports the error`() = runTest {
        val stored = repo.seed(book(title = "Keep me"))
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()
        repo.failWith = AppError.Storage("disk full")

        vm.onDelete()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.deleted)
        assertNotNull(vm.uiState.value.error)
        assertEquals(1, repo.all.size)
    }
}
