package app.otium.ui.add.search

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeMetadataRepository
import app.otium.domain.fakes.book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
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
class TitleSearchViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeBookRepository()
    private val metadata = FakeMetadataRepository()
    private val vm = TitleSearchViewModel(metadata, repo)
    private val state get() = vm.uiState.value

    private fun candidate(title: String, isbn13: String? = null) =
        BookCandidate(isbn13 = isbn13, title = title, source = BookSource.GOOGLE)

    @Test
    fun `a blank query cannot be searched`() {
        vm.onQueryChange("   ")

        assertFalse(state.canSearch)
    }

    @Test
    fun `searching shows the candidates in order`() = runTest {
        metadata.searchResult = Try.Success(listOf(candidate("Dune"), candidate("Dune Messiah")))
        vm.onQueryChange("dune herbert")

        vm.onSearch()
        assertTrue(state.isSearching)
        advanceUntilIdle()

        assertFalse(state.isSearching)
        assertEquals(listOf("Dune", "Dune Messiah"), state.results.map { it.title })
        assertEquals(listOf("dune herbert"), metadata.searched)
        assertFalse(state.noResults)
    }

    @Test
    fun `no match says so`() = runTest {
        vm.onQueryChange("zzzz")

        vm.onSearch()
        advanceUntilIdle()

        assertTrue(state.noResults)
    }

    @Test
    fun `a failed search keeps the query and reports the error`() = runTest {
        metadata.searchResult = Try.Failure(AppError.Offline)
        vm.onQueryChange("dune")

        vm.onSearch()
        advanceUntilIdle()

        assertEquals(AppError.Offline, state.error)
        assertEquals("dune", state.query)
        assertFalse(state.noResults)
    }

    @Test
    fun `choosing an edition goes to the confirm screen`() = runTest {
        val dune = candidate("Dune", isbn13 = "9780441013593")
        metadata.searchResult = Try.Success(listOf(dune))
        vm.onQueryChange("dune")
        vm.onSearch()
        advanceUntilIdle()

        vm.onResultChosen(dune)
        advanceUntilIdle()

        assertEquals(AddDestination.Confirm(dune), state.destination)
    }

    @Test
    fun `choosing an edition already owned opens that book`() = runTest {
        val owned = repo.seed(book(isbn13 = "9780441013593"))

        vm.onResultChosen(candidate("Dune", isbn13 = "9780441013593"))
        advanceUntilIdle()

        assertEquals(AddDestination.Book(owned.id), state.destination)
    }

    @Test
    fun `typing the details is always possible`() {
        vm.onQueryChange("an obscure pamphlet")

        vm.onEnterManually()

        val destination = state.destination as AddDestination.Confirm
        assertEquals("an obscure pamphlet", destination.candidate.title)
        assertEquals(BookSource.MANUAL, destination.candidate.source)

        vm.onNavigated()
        assertNull(state.destination)
    }
}
