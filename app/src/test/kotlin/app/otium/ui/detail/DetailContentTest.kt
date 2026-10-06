package app.otium.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.otium.domain.error.AppError
import app.otium.domain.fakes.book
import app.otium.domain.model.Book
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp")
class DetailContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val dune: Book = book(title = "Dune", totalPages = 400, currentPage = 100).copy(
        authors = listOf("Frank Herbert"),
        publisher = "Chilton Books",
        publishedYear = 1965,
        language = "en",
        isbn13 = "9780441172719",
        categories = listOf("Science fiction"),
        tags = listOf("favourite"),
        description = "A desert planet, a boy, and the spice.",
    )

    private fun show(state: DetailUiState, actions: DetailActions = DetailActions()) =
        rule.setContent { OtiumTheme(darkTheme = false) { DetailContent(state, actions) } }

    private fun typeInDialog(text: String) {
        rule.onNode(hasSetTextAction()).performTextReplacement(text)
    }

    private fun loaded(book: Book = dune, error: AppError? = null) =
        DetailUiState(isLoading = false, book = book, error = error)

    // --- Content ---

    @Test
    fun `shows what the book is`() {
        show(loaded())

        rule.onNodeWithText("Dune").assertIsDisplayed()
        rule.onNodeWithText("Frank Herbert").assertIsDisplayed()
        rule.onNodeWithText("In progress").assertIsDisplayed()
        rule.onNodeWithText("Page 100 of 400").assertIsDisplayed()
        rule.onNodeWithText("Science fiction").assertIsDisplayed()
        rule.onNodeWithText("favourite").assertIsDisplayed()
    }

    @Test
    fun `shows publication details`() {
        show(loaded())

        rule.onNodeWithText("Chilton Books").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1965").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("9780441172719").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a missing book says so instead of showing nothing`() {
        show(DetailUiState(isLoading = false, book = null))

        rule.onNodeWithText("This book is no longer in your library.").assertIsDisplayed()
    }

    @Test
    fun `while loading there is no not found message`() {
        show(DetailUiState(isLoading = true))

        rule.onAllNodesWithText("This book is no longer in your library.").assertCountEquals(0)
    }

    @Test
    fun `the back button leaves`() {
        var left = 0
        show(loaded(), DetailActions(onBack = { left++ }))

        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, left)
    }

    // --- Description ---

    @Test
    fun `a long description can be expanded and collapsed`() {
        val long = "A very long description. ".repeat(40)
        show(loaded(dune.copy(description = long)))

        rule.onNodeWithText("Show more").performScrollTo().performClick()
        rule.onNodeWithText("Show less").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Show less").performClick()

        rule.onNodeWithText("Show more").assertIsDisplayed()
    }

    @Test
    fun `a short description has no toggle`() {
        show(loaded())

        rule.onAllNodesWithText("Show more").assertCountEquals(0)
    }

    // --- Progress ---

    @Test
    fun `updating progress sends the typed page`() {
        var page = -1
        show(loaded(), DetailActions(onProgressConfirmed = { page = it }))

        rule.onNodeWithText("Update progress").performScrollTo().performClick()
        typeInDialog("250")
        rule.onNodeWithText("Save").performClick()
        rule.mainClock.advanceTimeByFrame()

        assertEquals(250, page)
    }

    @Test
    fun `cancelling the progress dialog changes nothing`() {
        var page = -1
        show(loaded(), DetailActions(onProgressConfirmed = { page = it }))

        rule.onNodeWithText("Update progress").performScrollTo().performClick()
        typeInDialog("250")
        rule.onNodeWithText("Cancel").performClick()
        rule.mainClock.advanceTimeByFrame()

        assertEquals(-1, page)
        rule.onAllNodesWithText("Current page").assertCountEquals(0)
    }

    @Test
    fun `an empty page cannot be saved`() {
        show(loaded())

        rule.onNodeWithText("Update progress").performScrollTo().performClick()
        typeInDialog("")

        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `mark finished is offered until the book is read`() {
        var finished = 0
        show(loaded(), DetailActions(onMarkFinished = { finished++ }))

        rule.onNodeWithText("Mark as finished").performScrollTo().performClick()

        assertEquals(1, finished)
    }

    @Test
    fun `a finished book has no mark finished button`() {
        show(loaded(dune.copy(currentPage = 400)))

        rule.onAllNodesWithText("Mark as finished").assertCountEquals(0)
    }

    @Test
    fun `an unknown total asks for the total instead of progress`() {
        var total = -1
        show(
            loaded(dune.copy(totalPages = 0, currentPage = 0)),
            DetailActions(onTotalPagesConfirmed = { total = it }),
        )
        rule.onAllNodesWithText("Update progress").assertCountEquals(0)
        rule.onAllNodesWithText("Mark as finished").assertCountEquals(0)
        rule.onNodeWithText("Total pages not set").assertIsDisplayed()

        rule.onNodeWithText("Set total pages").performScrollTo().performClick()
        typeInDialog("412")
        rule.onNodeWithText("Save").performClick()
        rule.mainClock.advanceTimeByFrame()

        assertEquals(412, total)
    }

    // --- Delete ---

    @Test
    fun `delete asks first and then deletes`() {
        var deleted = 0
        show(loaded(), DetailActions(onDelete = { deleted++ }))

        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Delete book").performClick()
        rule.onNodeWithText("Delete this book?").assertIsDisplayed()
        assertEquals(0, deleted)
        rule.onNodeWithText("Delete").performClick()

        assertEquals(1, deleted)
    }

    @Test
    fun `cancelling the delete keeps the book`() {
        var deleted = 0
        show(loaded(), DetailActions(onDelete = { deleted++ }))

        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Delete book").performClick()
        rule.onNodeWithText("Cancel").performClick()

        assertEquals(0, deleted)
        rule.onAllNodesWithText("Delete this book?").assertCountEquals(0)
    }

    // --- Errors ---

    @Test
    fun `an error is shown in words and then marked as shown`() {
        var shown = 0
        show(
            loaded(error = AppError.Storage("disk full")),
            DetailActions(onErrorShown = { shown++ }),
        )

        rule.onNodeWithText(
            "Something went wrong saving your library. Please try again.",
        ).assertIsDisplayed()
        rule.waitUntil(timeoutMillis = 10_000) { shown == 1 }

        assertTrue(shown >= 1)
    }

    @Test
    fun `the technical detail of an error is never shown`() {
        show(loaded(error = AppError.Storage("SQLITE_FULL disk full")))

        rule.onAllNodesWithText("SQLITE_FULL disk full", substring = true).assertCountEquals(0)
    }
}
