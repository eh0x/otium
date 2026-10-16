package app.otium.ui.library

import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLibraryPreferences
import app.otium.domain.fakes.FakeLoanRepository
import app.otium.domain.fakes.book
import app.otium.domain.model.BookStatus
import app.otium.domain.model.LibraryFilter
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.SortOrder
import app.otium.testing.FakeDebugTools
import app.otium.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeBookRepository()
    private val prefs = FakeLibraryPreferences()
    private val debugTools = FakeDebugTools()

    private val loans = FakeLoanRepository(repo)

    private fun viewModel(tools: FakeDebugTools = debugTools) =
        LibraryViewModel(repo, loans, prefs, tools)

    /** Keeps the state flow active the way an open screen does. */
    private fun TestScope.screenOpen(vm: LibraryViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    // --- Loading and content ---

    @Test
    fun `starts loading then shows the library`() = runTest {
        repo.seed(book(title = "Dune"))
        repo.seed(book(title = "Emma"))
        val vm = viewModel()
        screenOpen(vm)

        assertTrue(vm.uiState.value.isLoading)
        assertEquals(LibraryContentKind.LOADING, vm.uiState.value.contentKind)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Dune", "Emma"), state.books.map { it.title })
        assertEquals(2, state.totalBooks)
        assertEquals(LibraryContentKind.BOOKS, state.contentKind)
    }

    @Test
    fun `a library with no books is reported as empty`() = runTest {
        val vm = viewModel()
        screenOpen(vm)

        advanceUntilIdle()

        assertEquals(LibraryContentKind.EMPTY_LIBRARY, vm.uiState.value.contentKind)
    }

    @Test
    fun `no matches is different from an empty library`() = runTest {
        repo.seed(book(title = "Dune"))
        val vm = viewModel()
        screenOpen(vm)

        vm.onQueryChange("zzz")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.books.isEmpty())
        assertEquals(1, state.totalBooks)
        assertEquals(LibraryContentKind.NO_RESULTS, state.contentKind)
    }

    // --- Search ---

    @Test
    fun `typing shows the text at once but searches after the debounce`() = runTest {
        repo.seed(book(title = "Dune"))
        val vm = viewModel()
        screenOpen(vm)
        advanceUntilIdle()

        vm.onQueryChange("dun")
        runCurrent()
        assertEquals("dun", vm.uiState.value.query)

        advanceTimeBy(199)
        runCurrent()
        assertEquals("", repo.lastFilter?.query)

        advanceTimeBy(1)
        runCurrent()
        assertEquals("dun", repo.lastFilter?.query)
    }

    @Test
    fun `rapid typing searches only for the final text`() = runTest {
        repo.seed(book(title = "Dune"))
        val vm = viewModel()
        screenOpen(vm)
        advanceUntilIdle()

        vm.onQueryChange("d")
        advanceTimeBy(50)
        vm.onQueryChange("du")
        advanceTimeBy(50)
        vm.onQueryChange("dun")
        advanceUntilIdle()

        assertEquals(listOf("", "dun"), repo.filters.map { it.query })
    }

    @Test
    fun `clearing the search applies at once`() = runTest {
        val vm = viewModel()
        screenOpen(vm)
        vm.onQueryChange("dun")
        advanceUntilIdle()

        vm.onQueryChange("")
        runCurrent()

        assertEquals("", repo.lastFilter?.query)
    }

    // --- Filters and sorting ---

    @Test
    fun `status and sort combine into one filter`() = runTest {
        val vm = viewModel()
        screenOpen(vm)

        vm.onStatusSelected(BookStatus.READ)
        vm.onSortSelected(SortOrder.PROGRESS)
        advanceUntilIdle()

        assertEquals(
            LibraryFilter(status = BookStatus.READ, sort = SortOrder.PROGRESS),
            repo.lastFilter,
        )
    }

    @Test
    fun `status can be cleared back to all`() = runTest {
        val vm = viewModel()
        screenOpen(vm)

        vm.onStatusSelected(BookStatus.IN_PROGRESS)
        advanceUntilIdle()
        vm.onStatusSelected(null)
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.status)
        assertEquals(null, repo.lastFilter?.status)
    }

    @Test
    fun `clear filters resets search and status but keeps sort and view`() = runTest {
        val vm = viewModel()
        screenOpen(vm)
        vm.onQueryChange("dune")
        vm.onStatusSelected(BookStatus.READ)
        vm.onSortSelected(SortOrder.AUTHOR)
        vm.onViewModeSelected(LibraryViewMode.LIST)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasActiveFilters)

        vm.onClearFilters()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.hasActiveFilters)
        assertEquals("", state.query)
        assertEquals(SortOrder.AUTHOR, state.sort)
        assertEquals(LibraryViewMode.LIST, state.viewMode)
        assertEquals(LibraryFilter(sort = SortOrder.AUTHOR), repo.lastFilter)
    }

    // --- View mode ---

    @Test
    fun `a chosen series filters the library and the series list comes with progress`() = runTest {
        repo.seed(book(title = "Dune", series = "Dune", totalPages = 10, currentPage = 10))
        repo.seed(book(title = "Dune Messiah", series = "Dune", totalPages = 10))
        repo.seed(book(title = "Emma"))
        val vm = viewModel()
        screenOpen(vm)

        vm.onSeriesSelected("Dune")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Dune", repo.lastFilter?.series)
        assertEquals("Dune", state.series)
        assertEquals(listOf("Dune", "Dune Messiah"), state.books.map { it.title })
        assertEquals(listOf("Dune"), state.knownSeries.map { it.name })
        assertEquals(1, state.knownSeries.single().read)
    }

    @Test
    fun `clear filters also clears the series`() = runTest {
        repo.seed(book(title = "Dune", series = "Dune"))
        val vm = viewModel()
        screenOpen(vm)
        vm.onSeriesSelected("Dune")
        advanceUntilIdle()

        vm.onClearFilters()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.series)
        assertEquals(null, repo.lastFilter?.series)
    }

    @Test
    fun `the view mode is read from preferences and saved when changed`() = runTest {
        prefs.setViewMode(LibraryViewMode.LIST)
        val vm = viewModel()
        screenOpen(vm)
        advanceUntilIdle()
        assertEquals(LibraryViewMode.LIST, vm.uiState.value.viewMode)

        vm.onViewModeSelected(LibraryViewMode.SHELF)
        advanceUntilIdle()

        assertEquals(LibraryViewMode.SHELF, vm.uiState.value.viewMode)
        assertEquals(LibraryViewMode.SHELF, prefs.current)
    }

    // --- Debug tools ---

    @Test
    fun `debug tools are offered only when available and seeding is delegated`() = runTest {
        val vm = viewModel()
        screenOpen(vm)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.debugToolsAvailable)

        vm.onSeedDebugData()
        advanceUntilIdle()

        assertEquals(1, debugTools.seedCalls)
    }

    @Test
    fun `the library counts the books that are out, and only the open loans`() = runTest {
        val out = repo.seed(book(title = "Out")).id
        val back = repo.seed(book(title = "Back")).id
        loans.add(Loan(0L, out, "Sam", LoanDirection.LENT, lentOn = 5L))
        loans.add(Loan(0L, back, "Ana", LoanDirection.LENT, lentOn = 1L, returnedOn = 2L))
        val vm = viewModel()
        screenOpen(vm)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.openLoans)
    }

    @Test
    fun `a library with nothing lent offers no lent out link`() = runTest {
        repo.seed(book(title = "Home"))
        val vm = viewModel()
        screenOpen(vm)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.openLoans)
    }

    @Test
    fun `without debug tools nothing is offered`() = runTest {
        val vm = viewModel(tools = FakeDebugTools(isAvailable = false))
        screenOpen(vm)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.debugToolsAvailable)
    }
}
