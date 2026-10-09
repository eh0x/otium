package app.otium.ui.reading

import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.usecase.ObserveLibrary
import app.otium.domain.usecase.UpdateProgress
import app.otium.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val repo = FakeBookRepository()

    private fun viewModel() = ReadingViewModel(
        observeLibrary = ObserveLibrary(repo),
        updateProgress = UpdateProgress(repo, FixedClock()),
    )

    @Test
    fun `open books are those in progress and finished books those read`() = runTest {
        repo.seed(book(title = "Open", totalPages = 300, currentPage = 120))
        repo.seed(book(title = "Done", totalPages = 300, currentPage = 300))
        repo.seed(book(title = "Unread", totalPages = 300, currentPage = 0))
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("Open"), state.open.map { it.title })
        assertEquals(listOf("Done"), state.finished.map { it.title })
    }

    @Test
    fun `ten pages are added and kept within the book`() = runTest {
        val id = repo.seed(book(title = "Nearly", totalPages = 300, currentPage = 295)).id
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onAddPages(vm.uiState.value.open.single())
        advanceUntilIdle()

        assertEquals(300, repo.observeBook(id).first()?.currentPage)
    }
}
