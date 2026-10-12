package app.otium.ui.transfer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.otium.domain.transfer.ExportFormat
import app.otium.domain.transfer.ImportRow
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.usecase.ImportPreview
import app.otium.domain.usecase.ImportResult
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp")
class ImportExportContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(
        state: ImportExportUiState,
        actions: ImportExportActions = ImportExportActions(),
    ) = rule.setContent {
        OtiumTheme(darkTheme = false) { ImportExportContent(state = state, actions = actions) }
    }

    private fun preview(newBooks: Int, already: Int = 0, withoutIsbn: Int = 0) = ImportPreview(
        format = LibraryFormat.GOODREADS,
        rows = List(newBooks + already) { ImportRow(title = "Book $it") },
        newBooks = newBooks,
        alreadyInLibrary = already,
        withoutIsbn = withoutIsbn,
        unreadable = 0,
    )

    @Test
    fun `choosing a file and exporting are offered`() {
        val asked = mutableListOf<Any>()
        show(
            ImportExportUiState(bookCount = 3),
            ImportExportActions(
                onChooseFile = { asked += "file" },
                onExport = { asked += it },
            ),
        )

        rule.onNodeWithText("Choose a file").performClick()
        rule.onNodeWithText("Export as CSV").performScrollTo().performClick()
        rule.onNodeWithText("Export as JSON").performScrollTo().performClick()

        assertEquals(listOf("file", ExportFormat.CSV, ExportFormat.JSON), asked)
    }

    @Test
    fun `where to find an export is explained`() {
        show(ImportExportUiState())

        rule.onNodeWithText("From Goodreads").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("From StoryGraph").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an empty library cannot be exported`() {
        show(ImportExportUiState(bookCount = 0))

        rule.onNodeWithText("Your library is empty, so there is nothing to export yet.")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Export as CSV").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `the preview says what the file adds and leaves out, and asks first`() {
        var confirmed = 0
        show(
            ImportExportUiState(
                step = ImportStep.Previewing(preview(4, already = 2, withoutIsbn = 1)),
            ),
            ImportExportActions(onImportConfirmed = { confirmed++ }),
        )

        rule.onNodeWithText("Import 4 books?").assertIsDisplayed()
        rule.onNodeWithText("A library exported from Goodreads.").assertIsDisplayed()
        listOf(
            "2 books are already in your library and are left out.",
            "1 book has no ISBN, so it is added as the file has it.",
            "Ratings, reviews and reading dates are not imported.",
        ).forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Import").performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun `a file with nothing new can only be closed`() {
        var dismissed = 0
        show(
            ImportExportUiState(step = ImportStep.Previewing(preview(0, already = 3))),
            ImportExportActions(onImportDismissed = { dismissed++ }),
        )

        rule.onNodeWithText("Nothing new to import").assertIsDisplayed()
        rule.onNodeWithText("Import").assertDoesNotExist()
        rule.onNodeWithText("Close").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun `an import in progress shows how far it is and blocks a second one`() {
        show(
            ImportExportUiState(
                bookCount = 9,
                step = ImportStep.Importing(done = 120, total = 400),
            ),
        )

        rule.onNodeWithText("Adding books: 120 of 400").assertIsDisplayed()
        rule.onNodeWithText("Choose a file").assertIsNotEnabled()
        rule.onNodeWithText("Export as CSV").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `the result says what was added and what comes later`() {
        var dismissed = 0
        show(
            ImportExportUiState(
                step = ImportStep.Done(
                    ImportResult(added = 5, skipped = 2, failed = 1, waitingForLookup = 3),
                ),
            ),
            ImportExportActions(onResultDismissed = { dismissed++ }),
        )

        rule.onNodeWithText("5 books added.").assertIsDisplayed()
        rule.onNodeWithText("2 were already in your library.").assertIsDisplayed()
        rule.onNodeWithText("3 will get their covers and details when you are online.")
            .assertIsDisplayed()
        rule.onNodeWithText("1 could not be saved. Import the file again to try once more.")
            .assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()

        assertEquals(1, dismissed)
    }
}
