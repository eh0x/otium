package app.otium.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.otium.domain.fakes.book
import app.otium.domain.model.Book
import app.otium.domain.model.Note
import app.otium.domain.model.NoteKind
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp")
class NotesSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val dune: Book = book(title = "Dune", totalPages = 400, currentPage = 100)

    private fun note(id: Long, text: String, kind: NoteKind = NoteKind.NOTE, page: Int? = null) =
        Note(id, 1L, kind, text, page, createdAt = id, updatedAt = id)

    private fun show(notes: List<Note>, actions: DetailActions = DetailActions()) =
        rule.setContent {
            OtiumTheme(darkTheme = false) {
                DetailContent(DetailUiState(isLoading = false, book = dune, notes = notes), actions)
            }
        }

    private fun typeInto(label: String, text: String) {
        rule.onNode(
            hasContentDescription(label) and hasSetTextAction(),
        ).performTextReplacement(text)
    }

    private fun openAddDialog() {
        rule.onNodeWithText("Add").performScrollTo().performClick()
    }

    // --- Reading ---

    @Test
    fun `a book without notes says so and offers to add one`() {
        show(emptyList())

        rule.onNodeWithText("NOTES").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Nothing written yet", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Add").assertIsDisplayed()
    }

    @Test
    fun `notes show their text, kind and page`() {
        show(
            listOf(
                note(1, "He is not the one", NoteKind.QUOTE, page = 42),
                note(2, "Reread the appendix"),
            ),
        )

        rule.onNodeWithText("He is not the one").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("QUOTE").assertIsDisplayed()
        rule.onNodeWithText("p. 42").assertIsDisplayed()
        rule.onNodeWithText("Reread the appendix").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("NOTE").assertIsDisplayed()
    }

    // --- Adding ---

    @Test
    fun `saving a typed note hands back a draft with its page`() {
        var saved: NoteDraft? = null
        show(emptyList(), DetailActions(onNoteSaved = { saved = it }))

        openAddDialog()
        typeInto("Text", "  Fear is the mind-killer ")
        typeInto("Page (optional)", "12")
        rule.onNodeWithText("Save").performClick()

        assertEquals(NoteDraft(null, NoteKind.NOTE, "Fear is the mind-killer", 12), saved)
    }

    @Test
    fun `choosing quote saves a quote, and no page is allowed`() {
        var saved: NoteDraft? = null
        show(emptyList(), DetailActions(onNoteSaved = { saved = it }))

        openAddDialog()
        rule.onNodeWithText("Quote").performClick()
        typeInto("Text", "A passage")
        rule.onNodeWithText("Save").performClick()

        assertEquals(NoteDraft(null, NoteKind.QUOTE, "A passage", null), saved)
    }

    @Test
    fun `save waits for text and a page that makes sense`() {
        show(emptyList())

        openAddDialog()
        rule.onNodeWithText("Save").assertIsNotEnabled()

        typeInto("Text", "   ")
        rule.onNodeWithText("Save").assertIsNotEnabled()

        typeInto("Text", "Something")
        rule.onNodeWithText("Save").assertIsEnabled()

        typeInto("Page (optional)", "0")
        rule.onNodeWithText("Save").assertIsNotEnabled()

        typeInto("Page (optional)", "5")
        rule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `text over the limit counts the characters and cannot be saved`() {
        show(emptyList())

        openAddDialog()
        typeInto("Text", "x".repeat(Note.MAX_TEXT_LENGTH + 1))

        rule.onNodeWithText("10001 of 10000 characters").assertIsDisplayed()
        rule.onNodeWithText("Save").assertIsNotEnabled()

        typeInto("Text", "x".repeat(Note.MAX_TEXT_LENGTH))
        rule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `cancelling the editor saves nothing`() {
        var saved: NoteDraft? = null
        show(emptyList(), DetailActions(onNoteSaved = { saved = it }))

        openAddDialog()
        typeInto("Text", "Never mind")
        rule.onNodeWithText("Cancel").performClick()

        assertNull(saved)
        rule.onAllNodesWithText("New note").assertCountEquals(0)
    }

    // --- Editing and deleting ---

    @Test
    fun `editing opens the note filled in and saves over it`() {
        val original = note(7, "First thought", NoteKind.NOTE, page = 3)
        var saved: NoteDraft? = null
        show(listOf(original), DetailActions(onNoteSaved = { saved = it }))

        rule.onNodeWithContentDescription(
            "Edit note: First thought",
        ).performScrollTo().performClick()
        rule.onNodeWithText("Edit note").assertIsDisplayed()
        typeInto("Text", "Second thought")
        rule.onNodeWithText("Save").performClick()

        assertEquals(NoteDraft(original, NoteKind.NOTE, "Second thought", 3), saved)
    }

    @Test
    fun `deleting asks first and then removes the note`() {
        var deleted: Long? = null
        show(listOf(note(7, "Doomed")), DetailActions(onNoteDeleted = { deleted = it }))

        rule.onNodeWithContentDescription("Delete note: Doomed").performScrollTo().performClick()
        rule.onNodeWithText("Delete this note?").assertIsDisplayed()
        assertNull(deleted)

        rule.onNodeWithText("Delete").performClick()

        assertEquals(7L, deleted)
    }

    @Test
    fun `cancelling the delete keeps the note`() {
        var deleted: Long? = null
        show(listOf(note(7, "Safe")), DetailActions(onNoteDeleted = { deleted = it }))

        rule.onNodeWithContentDescription("Delete note: Safe").performScrollTo().performClick()
        rule.onNodeWithText("Cancel").performClick()

        assertNull(deleted)
        rule.onNodeWithText("Safe").assertIsDisplayed()
    }

    // --- Searching ---

    private fun manyNotes() = (1L..6L).map { note(it, "Note about topic $it") } +
        note(7, "Something about the spice")

    @Test
    fun `a few notes have no search`() {
        show(listOf(note(1, "one"), note(2, "two")))

        rule.onAllNodesWithText("Search these notes").assertCountEquals(0)
    }

    @Test
    fun `many notes can be searched, ignoring case`() {
        show(manyNotes())

        typeInto("Search these notes", "SPICE")

        rule.onNodeWithText("Something about the spice").assertIsDisplayed()
        rule.onAllNodesWithText("Note about topic", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a search with no match says so`() {
        show(manyNotes())

        typeInto("Search these notes", "worm")

        rule.onNodeWithText("No note mentions “worm”.").assertIsDisplayed()
    }
}
