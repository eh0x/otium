package app.otium.ui.transfer

import app.otium.domain.error.AppError
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeLibraryFiles
import app.otium.domain.fakes.FakeLibraryJson
import app.otium.domain.fakes.FakeLibrarySnapshot
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.transfer.ExportFormat
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.usecase.BackupLock
import app.otium.domain.usecase.ExportLibrary
import app.otium.domain.usecase.ImportLibrary
import app.otium.domain.usecase.ImportResult
import app.otium.domain.usecase.ReadLibraryFile
import app.otium.testing.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportExportViewModelTest {

    @get:Rule
    val dispatcher = MainDispatcherRule()

    private val repo = FakeBookRepository()
    private val files = FakeLibraryFiles()
    private var scheduled = 0

    private fun viewModel() = ImportExportViewModel(
        importLibrary = ImportLibrary(
            books = repo,
            readFile = ReadLibraryFile(files, FakeLibraryJson(), Dispatchers.Unconfined),
            clock = FixedClock(),
            scheduler = { scheduled++ },
            lock = BackupLock(),
        ),
        exportLibrary = ExportLibrary(
            FakeLibrarySnapshot(repo),
            files,
            FakeLibraryJson(),
            FixedClock(now = 3L),
            Dispatchers.Unconfined,
        ),
        books = repo,
    )

    private val goodreads = """
        Title,Author,Exclusive Shelf,ISBN13,Number of Pages
        Dune,Frank Herbert,read,9780441013593,412
        Notes,Someone,to-read,,
    """.trimIndent()

    @Test
    fun `a picked file is previewed, then imported once confirmed`() = runTest {
        files.files["content://lib.csv"] = goodreads
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onFilePicked("content://lib.csv")
        advanceUntilIdle()

        val preview = (vm.uiState.value.step as ImportStep.Previewing).preview
        assertEquals(LibraryFormat.GOODREADS, preview.format)
        assertEquals(2, preview.newBooks)
        assertTrue("nothing is written before the owner confirms", repo.all.isEmpty())

        vm.onImportConfirmed()
        advanceUntilIdle()

        assertEquals(
            ImportStep.Done(ImportResult(added = 2, skipped = 0, failed = 0, waitingForLookup = 1)),
            vm.uiState.value.step,
        )
        assertEquals(2, vm.uiState.value.bookCount)
        assertEquals(1, scheduled)

        vm.onResultDismissed()
        advanceUntilIdle()
        assertEquals(ImportStep.Idle, vm.uiState.value.step)
    }

    @Test
    fun `cancelling the picker or the preview changes nothing`() = runTest {
        files.files["content://lib.csv"] = goodreads
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onFilePicked(null)
        assertEquals(ImportStep.Idle, vm.uiState.value.step)

        vm.onFilePicked("content://lib.csv")
        advanceUntilIdle()
        vm.onImportDismissed()
        advanceUntilIdle()

        assertEquals(ImportStep.Idle, vm.uiState.value.step)
        assertTrue(repo.all.isEmpty())
    }

    @Test
    fun `a file in no known format is reported and nothing is previewed`() = runTest {
        files.files["content://notes.txt"] = "Name,Price\nTea,3\n"
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onFilePicked("content://notes.txt")
        advanceUntilIdle()

        assertEquals(ImportStep.Idle, vm.uiState.value.step)
        assertEquals(AppError.UnknownFileFormat, vm.uiState.value.error)
        vm.onErrorShown()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `an export writes the file and reports how many books it holds`() = runTest {
        repo.seed(book(title = "One"))
        repo.seed(book(title = "Two"))
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canExport)

        vm.onExport("content://out.json", ExportFormat.JSON)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.exported)
        assertNull(vm.uiState.value.exporting)
        assertEquals("{\"books\":2,\"exportedAt\":3}", files.files["content://out.json"])
        vm.onExportedShown()
        advanceUntilIdle()
        assertNull(vm.uiState.value.exported)
    }

    @Test
    fun `an export that cannot be written is reported`() = runTest {
        repo.seed(book())
        files.failWith = AppError.Storage("gone")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onExport("content://out.csv", ExportFormat.CSV)
        advanceUntilIdle()

        assertEquals(AppError.Storage("gone"), vm.uiState.value.error)
        assertNull(vm.uiState.value.exporting)
    }

    @Test
    fun `an empty library cannot be exported`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canExport)
    }
}
