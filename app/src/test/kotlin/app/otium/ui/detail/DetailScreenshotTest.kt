package app.otium.ui.detail

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.otium.domain.fakes.book
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.ui.saveScreenshot
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the book detail headlessly to build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class DetailScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val meditations = book(title = "Meditations", totalPages = 304, currentPage = 212).copy(
        authors = listOf("Marcus Aurelius"),
        publisher = "Penguin Classics",
        publishedYear = 2006,
        language = "en",
        isbn13 = "9780140449334",
        categories = listOf("Philosophy"),
        description = "Written in Greek by the only Roman emperor who was also a philosopher, " +
            "without any intention of publication, the Meditations of Marcus Aurelius offer a " +
            "remarkable series of challenging spiritual reflections and exercises developed as " +
            "the emperor struggled to understand himself and make sense of the universe.",
    )

    private fun render(name: String, dark: Boolean) {
        rule.setContent {
            OtiumTheme(darkTheme = dark) {
                DetailContent(DetailUiState(isLoading = false, book = meditations), DetailActions())
            }
        }
        rule.saveScreenshot(name)
    }

    @Test
    fun `detail in the light theme`() = render("detail-light", dark = false)

    @Test
    fun `detail in the dark theme`() = render("detail-dark", dark = true)

    private val notes = listOf(
        Note(1, 0, NoteKind.QUOTE, "You have power over your mind, not outside events.", 31, 1, 1),
        Note(2, 0, NoteKind.NOTE, "Reread book II on a slow afternoon.", 74, 2, 2),
        Note(3, 0, NoteKind.NOTE, "Compare with the Enchiridion.", null, 3, 3),
    )

    private fun renderNotes(name: String, dark: Boolean, editor: Boolean = false) {
        rule.setContent {
            OtiumTheme(darkTheme = dark) {
                val state = DetailUiState(isLoading = false, book = meditations, notes = notes)
                DetailContent(state, DetailActions())
            }
        }
        rule.onNodeWithText("NOTES").performScrollTo()
        if (editor) rule.onNodeWithText("Add").performClick()
        rule.saveScreenshot(name)
    }

    @Test
    fun `notes in the light theme`() = renderNotes("detail-notes-light", dark = false)

    @Test
    fun `notes in the dark theme`() = renderNotes("detail-notes-dark", dark = true)

    @Test
    fun `the note editor`() = renderNotes("detail-note-editor-light", dark = false, editor = true)
}
