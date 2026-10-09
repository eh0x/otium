package app.otium.ui.reading

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.otium.domain.model.BookSummary
import app.otium.domain.usecase.DeriveStatus
import app.otium.ui.saveScreenshot
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
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class ReadingContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun summary(id: Long, title: String, authors: String, current: Int, total: Int) =
        BookSummary(
            id = id,
            title = title,
            authors = authors,
            coverPath = null,
            coverSourceUrl = null,
            currentPage = current,
            totalPages = total,
            status = DeriveStatus(current, total),
        )

    private val meditations = summary(1, "Meditations", "Marcus Aurelius", 212, 304)
    private val ethics = summary(2, "Ethics", "Baruch Spinoza", 300, 300)

    private fun show(state: ReadingUiState, actions: ReadingActions = ReadingActions()) =
        rule.setContent { OtiumTheme(darkTheme = false) { ReadingContent(state, actions) } }

    @Test
    fun `an open book shows its page and moves on ten pages with one tap`() {
        val added = mutableListOf<BookSummary>()
        show(
            ReadingUiState(isLoading = false, open = listOf(meditations)),
            ReadingActions(onAddPages = { added += it }),
        )

        rule.onNodeWithText("212").assertIsDisplayed()
        rule.onNodeWithText("of 304 pages").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add 10 pages to Meditations").performClick()

        assertEquals(listOf(meditations), added)
    }

    @Test
    fun `open leads to the book`() {
        var opened = -1L
        show(
            ReadingUiState(isLoading = false, open = listOf(meditations)),
            ReadingActions(onOpenBook = { opened = it }),
        )

        rule.onNodeWithContentDescription("Open Meditations").performClick()

        assertEquals(1L, opened)
    }

    @Test
    fun `with nothing open it says so, and still lists what was finished`() {
        var opened = -1L
        show(
            ReadingUiState(isLoading = false, finished = listOf(ethics)),
            ReadingActions(onOpenBook = { opened = it }),
        )

        rule.onNodeWithText("Nothing open at the moment. Begin a book from your library.")
            .assertIsDisplayed()
        rule.onNodeWithText("Recently finished").assertIsDisplayed()
        rule.onNodeWithText("Ethics").performClick()

        assertEquals(2L, opened)
    }

    @Test
    fun `renders for review`() {
        val state =
            ReadingUiState(isLoading = false, open = listOf(meditations), finished = listOf(ethics))
        show(state)
        rule.saveScreenshot("reading-light")
    }
}
