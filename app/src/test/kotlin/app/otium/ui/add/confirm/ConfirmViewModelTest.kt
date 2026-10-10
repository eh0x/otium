package app.otium.ui.add.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.testing.invoke
import app.otium.domain.error.AppError
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeCoverStore
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.model.CoverChoice
import app.otium.domain.usecase.SaveBook
import app.otium.testing.MainDispatcherRule
import app.otium.ui.navigation.ConfirmRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric because the route arguments are read through an Android Bundle. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConfirmViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = FakeBookRepository()
    private val covers = FakeCoverStore()
    private val saveBook = SaveBook(repo, covers, FixedClock(now = 4_000L))

    private val found = BookCandidate(
        isbn13 = "9780441013593",
        title = "Dune",
        authors = listOf("Frank Herbert", "Brian Herbert"),
        publishedYear = 1965,
        totalPages = 412,
        categories = listOf("Fiction"),
        coverUrl = "https://google/dune.jpg",
        alternateCoverUrls = listOf("https://ol/dune.jpg"),
        source = BookSource.GOOGLE,
    )

    /** What the screen handed over for deleting when it went. */
    private val discarded = mutableListOf<String>()

    private fun viewModel(route: ConfirmRoute = ConfirmRoute.from(found)) =
        ConfirmViewModel(SavedStateHandle(route = route), repo, saveBook) { discarded += it }

    /** A view model owned by [store], so clearing the store ends it as leaving the screen does. */
    private fun ownedBy(store: ViewModelStore): ConfirmViewModel =
        ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[
            ConfirmViewModel::class,
        ]

    private fun TestScope.screenOpen(vm: ConfirmViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private fun TestScope.opened(route: ConfirmRoute = ConfirmRoute.from(found)): ConfirmViewModel =
        viewModel(route).also {
            screenOpen(it)
            advanceUntilIdle()
        }

    private val ConfirmViewModel.state get() = uiState.value

    // --- A new book ---

    @Test
    fun `the form starts with what the lookup found`() = runTest {
        val vm = opened()

        val form = vm.state.form
        assertEquals("Dune", form.title)
        assertEquals("Frank Herbert, Brian Herbert", form.authors)
        assertEquals("1965", form.publishedYear)
        assertEquals("412", form.totalPages)
        assertEquals("9780441013593", form.isbn)
        assertFalse(vm.state.isEditing)
        assertEquals(CoverChoice.Remote("https://google/dune.jpg"), vm.state.cover)
        assertEquals(
            listOf("https://google/dune.jpg", "https://ol/dune.jpg"),
            vm.state.remoteCovers,
        )
    }

    @Test
    fun `an empty form for a book nobody knows keeps the isbn and asks for pages`() = runTest {
        val vm = opened(ConfirmRoute(isbn13 = "9780441013593"))

        assertEquals("9780441013593", vm.state.form.isbn)
        assertEquals("", vm.state.form.title)
        assertTrue(vm.state.pagesUnknown)
        assertFalse("saving needs a title", vm.state.canSave)
    }

    @Test
    fun `saving a looked up book unchanged keeps it open to later refresh`() = runTest {
        val vm = opened()

        vm.onSave()
        advanceUntilIdle()

        val saved = repo.all.single()
        assertEquals(saved.id, vm.state.savedBookId)
        assertEquals("Dune", saved.title)
        assertEquals(listOf("Frank Herbert", "Brian Herbert"), saved.authors)
        assertEquals(412, saved.totalPages)
        assertEquals("the remote cover is downloaded", covers.stored.single(), saved.coverPath)
        assertFalse(saved.userEdited)
    }

    @Test
    fun `saving after an edit protects the book from refresh`() = runTest {
        val vm = opened()

        vm.onFormChange(
            vm.state.form.copy(title = "Dune (50th anniversary)", tags = listOf("gift")),
        )
        vm.onSave()
        advanceUntilIdle()

        val saved = repo.all.single()
        assertEquals("Dune (50th anniversary)", saved.title)
        assertEquals(listOf("gift"), saved.tags)
        assertTrue(saved.userEdited)
    }

    @Test
    fun `an alternate cover can be chosen`() = runTest {
        val vm = opened()

        vm.onCoverSelected(CoverChoice.Remote("https://ol/dune.jpg"))
        vm.onSave()
        advanceUntilIdle()

        assertEquals("https://ol/dune.jpg", repo.all.single().coverSourceUrl)
    }

    @Test
    fun `the cover can be removed`() = runTest {
        val vm = opened()

        vm.onCoverSelected(CoverChoice.None)
        vm.onSave()
        advanceUntilIdle()

        assertNull(repo.all.single().coverPath)
        assertTrue(covers.stored.isEmpty())
    }

    @Test
    fun `a picked photo is stored with the book`() = runTest {
        val vm = opened()

        vm.onCoverSelected(CoverChoice.Picked("content://photos/7"))
        vm.onSave()
        advanceUntilIdle()

        assertEquals(listOf("content://photos/7"), covers.pickedUris)
        assertEquals(covers.stored.single(), repo.all.single().coverPath)
    }

    @Test
    fun `every photo picked is handed over for deleting once the screen goes`() = runTest {
        val store = ViewModelStore()
        val vm = ownedBy(store)

        vm.onCoverSelected(CoverChoice.Picked("content://photos/1"))
        vm.onCoverSelected(CoverChoice.Picked("content://photos/2"))
        vm.onCoverSelected(CoverChoice.None)
        assertTrue("nothing goes while the screen is open", discarded.isEmpty())

        store.clear()

        assertEquals(listOf("content://photos/1", "content://photos/2"), discarded)
    }

    @Test
    fun `a book already in the library is reported with its id`() = runTest {
        val existing = repo.seed(book(isbn13 = "9780441013593", title = "Dune"))
        val vm = opened()

        vm.onSave()
        advanceUntilIdle()

        assertEquals(existing.id, vm.state.duplicateOf)
        assertNull(vm.state.savedBookId)
        assertFalse(vm.state.isSaving)

        vm.onDuplicateDismissed()
        advanceUntilIdle()
        assertNull(vm.state.duplicateOf)
    }

    @Test
    fun `an invalid isbn is reported and nothing is saved`() = runTest {
        val vm = opened()

        vm.onFormChange(vm.state.form.copy(isbn = "12345"))
        vm.onSave()
        advanceUntilIdle()

        assertEquals(AppError.InvalidIsbn("12345"), vm.state.error)
        assertTrue(repo.all.isEmpty())

        vm.onErrorShown()
        advanceUntilIdle()
        assertNull(vm.state.error)
    }

    @Test
    fun `numbers that are not numbers are treated as unknown`() = runTest {
        val vm = opened()

        vm.onFormChange(vm.state.form.copy(totalPages = "lots", publishedYear = "?"))
        vm.onSave()
        advanceUntilIdle()

        val saved = repo.all.single()
        assertEquals(0, saved.totalPages)
        assertNull(saved.publishedYear)
    }

    @Test
    fun `tags already in the library are offered`() = runTest {
        repo.seed(book(title = "Other", tags = listOf("classic", "scifi")))
        val vm = opened()

        assertEquals(listOf("classic", "scifi"), vm.state.knownTags)
    }

    // --- Editing a book ---

    @Test
    fun `editing loads the book into the form`() = runTest {
        val stored = repo.seed(
            book(isbn13 = "9780441013593", title = "Dune", totalPages = 400, currentPage = 90)
                .copy(coverPath = "covers/dune.jpg", tags = listOf("favourite")),
        )
        val vm = opened(ConfirmRoute.edit(stored.id))

        assertTrue(vm.state.isEditing)
        assertFalse(vm.state.isLoading)
        assertEquals("Dune", vm.state.form.title)
        assertEquals("400", vm.state.form.totalPages)
        assertEquals(listOf("favourite"), vm.state.form.tags)
        assertEquals(CoverChoice.Unchanged, vm.state.cover)
        assertEquals("covers/dune.jpg", vm.state.currentCoverPath)
    }

    @Test
    fun `saving an edit updates the book and keeps its progress`() = runTest {
        val stored = repo.seed(book(title = "Dune", totalPages = 400, currentPage = 90))
        val vm = opened(ConfirmRoute.edit(stored.id))

        vm.onFormChange(vm.state.form.copy(title = "Dune Messiah"))
        vm.onSave()
        advanceUntilIdle()

        val saved = repo.all.single()
        assertEquals(stored.id, vm.state.savedBookId)
        assertEquals("Dune Messiah", saved.title)
        assertEquals(90, saved.currentPage)
        assertTrue(saved.userEdited)
    }

    @Test
    fun `editing a book that is gone says so`() = runTest {
        val vm = opened(ConfirmRoute.edit(99L))

        assertTrue(vm.state.notFound)
        assertFalse(vm.state.canSave)
    }
}
