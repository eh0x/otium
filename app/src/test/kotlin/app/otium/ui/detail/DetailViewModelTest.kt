package app.otium.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.otium.domain.error.AppError
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLoanReminderScheduler
import app.otium.domain.fakes.FakeLoanRepository
import app.otium.domain.fakes.FakeNoteRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.NoteKind
import app.otium.domain.usecase.LendingLedger
import app.otium.domain.usecase.ReadingTracker
import app.otium.domain.usecase.SaveNote
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
    private val notes = FakeNoteRepository(knownBooks = setOf(1L))
    private val loans = FakeLoanRepository(repo)
    private val reminders = FakeLoanReminderScheduler()

    private fun viewModel(id: Long) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("bookId" to id)),
        repository = repo,
        reading = ReadingTracker(repo, clock),
        noteRepository = notes,
        saveNote = SaveNote(notes, clock),
        ledger = LendingLedger(loans, reminders, clock),
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

    // --- Notes ---

    @Test
    fun `a new note is stored on the book and shows in state`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onNoteSaved(NoteDraft(null, NoteKind.QUOTE, " A passage ", page = 42))
        advanceUntilIdle()

        val note = vm.uiState.value.notes.single()
        assertEquals(NoteKind.QUOTE, note.kind)
        assertEquals("A passage", note.text)
        assertEquals(42, note.page)
        assertEquals(stored.id, note.bookId)
        assertEquals(5_000L, note.createdAt)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `editing a note changes it and keeps when it was written`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()
        vm.onNoteSaved(NoteDraft(null, NoteKind.NOTE, "first", page = null))
        advanceUntilIdle()
        val original = vm.uiState.value.notes.single()

        vm.onNoteSaved(NoteDraft(original, NoteKind.QUOTE, "second", page = 7))
        advanceUntilIdle()

        val changed = vm.uiState.value.notes.single()
        assertEquals(original.id, changed.id)
        assertEquals("second", changed.text)
        assertEquals(NoteKind.QUOTE, changed.kind)
        assertEquals(7, changed.page)
        assertEquals(original.createdAt, changed.createdAt)
    }

    @Test
    fun `blank text is reported and nothing is stored`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onNoteSaved(NoteDraft(null, NoteKind.NOTE, "   ", page = null))
        advanceUntilIdle()

        assertEquals(AppError.InvalidInput("text"), vm.uiState.value.error)
        assertTrue(vm.uiState.value.notes.isEmpty())
    }

    @Test
    fun `lending a book counts the days from today and plans the reminder`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onLoanOpened(LoanDraft(LoanDirection.LENT, " Sam ", daysAgo = 1, remindInDays = 14))
        advanceUntilIdle()

        val loan = vm.uiState.value.loans.single()
        assertEquals("Sam", loan.counterparty)
        assertEquals(LoanDirection.LENT, loan.direction)
        assertEquals(-1L, loan.lentOn)
        assertEquals(14L, loan.remindOn)
        assertEquals(mapOf(loan.id to 14L), reminders.planned)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `a blank name is reported and nothing is lent`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onLoanOpened(LoanDraft(LoanDirection.LENT, "  ", daysAgo = 0, remindInDays = null))
        advanceUntilIdle()

        assertEquals(AppError.InvalidInput("name"), vm.uiState.value.error)
        assertTrue(vm.uiState.value.loans.isEmpty())
    }

    @Test
    fun `a book already out cannot be lent again`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()
        vm.onLoanOpened(LoanDraft(LoanDirection.LENT, "Sam", daysAgo = 0, remindInDays = null))
        advanceUntilIdle()

        vm.onLoanOpened(LoanDraft(LoanDirection.LENT, "Ana", daysAgo = 0, remindInDays = null))
        advanceUntilIdle()

        assertEquals(AppError.AlreadyOnLoan, vm.uiState.value.error)
        assertEquals(listOf("Sam"), vm.uiState.value.loans.map { it.counterparty })
    }

    @Test
    fun `returning a loan keeps it as history and drops its reminder`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()
        vm.onLoanOpened(LoanDraft(LoanDirection.BORROWED, "Ana", daysAgo = 0, remindInDays = 7))
        advanceUntilIdle()

        vm.onLoanReturned(vm.uiState.value.loans.single().id)
        advanceUntilIdle()

        val loan = vm.uiState.value.loans.single()
        assertEquals(0L, loan.returnedOn)
        assertTrue(reminders.planned.isEmpty())
    }

    @Test
    fun `a reminder can be moved, cleared and a loan removed from the history`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()
        vm.onLoanOpened(LoanDraft(LoanDirection.LENT, "Sam", daysAgo = 0, remindInDays = null))
        advanceUntilIdle()
        val id = vm.uiState.value.loans.single().id

        vm.onReminderChanged(id, 30)
        advanceUntilIdle()
        assertEquals(30L, vm.uiState.value.loans.single().remindOn)

        vm.onReminderChanged(id, null)
        advanceUntilIdle()
        assertNull(vm.uiState.value.loans.single().remindOn)

        vm.onLoanRemoved(id)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loans.isEmpty())
    }

    @Test
    fun `deleting a note removes it and leaves the book`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()
        vm.onNoteSaved(NoteDraft(null, NoteKind.NOTE, "gone soon", page = null))
        advanceUntilIdle()

        vm.onNoteDeleted(vm.uiState.value.notes.single().id)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notes.isEmpty())
        assertNotNull(vm.uiState.value.book)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `deleting a note that is already gone is reported`() = runTest {
        val stored = repo.seed(book())
        val vm = viewModel(stored.id)
        screenOpen(vm)
        advanceUntilIdle()

        vm.onNoteDeleted(404L)
        advanceUntilIdle()

        assertEquals(AppError.NotFound, vm.uiState.value.error)
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
