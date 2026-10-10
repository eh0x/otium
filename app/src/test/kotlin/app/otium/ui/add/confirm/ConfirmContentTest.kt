package app.otium.ui.add.confirm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.otium.domain.model.CoverChoice
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
class ConfirmContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val forms = mutableListOf<BookForm>()
    private val covers = mutableListOf<CoverChoice>()
    private var saves = 0
    private val opened = mutableListOf<Long>()

    private val actions = ConfirmActions(
        onFormChange = { forms += it },
        onCoverSelected = { covers += it },
        onSave = { saves++ },
        onOpenBook = { opened += it },
    )

    private val dune = ConfirmUiState(
        form = BookForm(title = "Dune", authors = "Frank Herbert", totalPages = "412"),
        cover = CoverChoice.Remote("https://google/dune.jpg"),
        remoteCovers = listOf("https://google/dune.jpg", "https://ol/dune.jpg"),
    )

    private fun show(state: ConfirmUiState) =
        rule.setContent { OtiumTheme(darkTheme = false) { ConfirmContent(state, actions) } }

    private fun openMoreDetails() {
        rule.onNodeWithText("More details").performScrollTo().performClick()
    }

    @Test
    fun `shows the details to check and saves them`() {
        show(dune)

        rule.onNodeWithText("Check the details").assertIsDisplayed()
        rule.onNodeWithText("Dune").assertIsDisplayed()
        rule.onNodeWithText("Add to library").assertIsEnabled().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun `a missing title blocks saving and says why`() {
        show(dune.copy(form = BookForm()))

        rule.onNodeWithText("Add to library").assertIsNotEnabled()
        rule.onNodeWithText("A title is needed to save the book").assertIsDisplayed()
    }

    @Test
    fun `typing reports the changed form`() {
        show(dune)

        rule.onNodeWithText("Dune").performTextReplacement("Dune Messiah")

        assertEquals("Dune Messiah", forms.last().title)
    }

    @Test
    fun `an unknown page count is asked for`() {
        show(dune.copy(form = dune.form.copy(totalPages = "")))

        rule.onNodeWithText("Add the page count to follow your progress")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `another offered cover can be chosen or the cover dropped`() {
        show(dune)

        rule.onNodeWithContentDescription("Cover option 2").performClick()
        rule.onNodeWithText("No cover").performClick()

        assertEquals(
            listOf(CoverChoice.Remote("https://ol/dune.jpg"), CoverChoice.None),
            covers,
        )
    }

    @Test
    fun `a typed tag is added`() {
        show(dune)

        openMoreDetails()
        rule.onNodeWithText("ADD A TAG").performScrollTo().performTextInput("gift")
        rule.onNodeWithText("ADD A TAG").performImeAction()

        assertEquals(listOf("gift"), forms.last().tags)
    }

    @Test
    fun `a known tag is suggested while typing`() {
        show(dune.copy(knownTags = listOf("classic", "scifi")))

        openMoreDetails()
        rule.onNodeWithText("ADD A TAG").performScrollTo().performTextInput("cl")
        rule.onNodeWithText("classic").performScrollTo().performClick()

        assertEquals(listOf("classic"), forms.last().tags)
    }

    @Test
    fun `a tag can be removed`() {
        show(dune.copy(form = dune.form.copy(tags = listOf("gift", "scifi"))))

        openMoreDetails()
        rule.onNodeWithContentDescription("Remove tag gift").performScrollTo().performClick()

        assertEquals(listOf("scifi"), forms.last().tags)
    }

    @Test
    fun `the shelf is chosen from the library's categories`() {
        show(
            dune.copy(
                form = dune.form.copy(categories = "Fiction"),
                knownCategories = listOf("Philosophy"),
            ),
        )

        rule.onNodeWithText("Fiction").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Philosophy").performScrollTo().assertIsNotSelected().performClick()

        assertEquals("Philosophy, Fiction", forms.last().categories)
    }

    @Test
    fun `the rarer fields wait under more details`() {
        show(dune)

        rule.onNodeWithText("ISBN").assertDoesNotExist()
        openMoreDetails()

        rule.onNodeWithText("ISBN").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a duplicate offers the book already on the shelves`() {
        show(dune.copy(duplicateOf = 7L))

        rule.onNodeWithText("Already in your library").assertIsDisplayed()
        rule.onNodeWithText("Open book").performClick()

        assertEquals(listOf(7L), opened)
    }

    @Test
    fun `editing a book that is gone says so`() {
        show(ConfirmUiState(isEditing = true, notFound = true))

        rule.onNodeWithText("Edit book").assertIsDisplayed()
        rule.onNodeWithText("This book is no longer in your library.").assertIsDisplayed()
    }
}
