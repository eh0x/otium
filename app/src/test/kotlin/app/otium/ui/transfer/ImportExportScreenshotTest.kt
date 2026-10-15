package app.otium.ui.transfer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.otium.domain.transfer.ImportRow
import app.otium.domain.transfer.LibraryFormat
import app.otium.domain.usecase.ImportPreview
import app.otium.ui.saveScreenshot
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders Import and export, headlessly to build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1100dp-xxhdpi")
class ImportExportScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(name: String, state: ImportExportUiState, dark: Boolean = false) {
        rule.setContent {
            OtiumTheme(darkTheme = dark) {
                ImportExportContent(state = state, actions = ImportExportActions())
            }
        }
        rule.saveScreenshot(name)
    }

    @Test
    fun `import and export in the light theme`() =
        render("import-export-light", ImportExportUiState(bookCount = 12))

    @Test
    fun `import and export in the dark theme`() =
        render("import-export-dark", ImportExportUiState(bookCount = 12), dark = true)

    @Test
    fun `an import in progress`() = render(
        "import-export-importing",
        ImportExportUiState(bookCount = 12, step = ImportStep.Importing(done = 120, total = 400)),
    )

    @Test
    fun `the preview of a goodreads file`() = render(
        "import-export-preview",
        ImportExportUiState(
            bookCount = 12,
            step = ImportStep.Previewing(
                ImportPreview(
                    format = LibraryFormat.GOODREADS,
                    rows = List(412) { ImportRow(title = "Book $it") },
                    newBooks = 400,
                    alreadyInLibrary = 12,
                    withoutIsbn = 9,
                    unreadable = 1,
                ),
            ),
        ),
    )
}
