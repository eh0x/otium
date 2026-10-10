package app.otium.ui.add

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.ui.add.isbn.IsbnEntryActions
import app.otium.ui.add.isbn.IsbnEntryContent
import app.otium.ui.add.isbn.IsbnEntryUiState
import app.otium.ui.add.search.TitleSearchActions
import app.otium.ui.add.search.TitleSearchContent
import app.otium.ui.add.search.TitleSearchUiState
import app.otium.ui.saveScreenshot
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the ISBN entry and title search screens headlessly to build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class AddFlowScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `isbn entry while consulting the catalogue`() {
        rule.setContent {
            OtiumTheme(darkTheme = false) {
                IsbnEntryContent(
                    IsbnEntryUiState(input = "9780140449334", isLooking = true),
                    IsbnEntryActions(),
                )
            }
        }
        rule.saveScreenshot("isbn-light")
    }

    @Test
    fun `title search with results`() {
        val results = listOf("Meditations" to "Marcus Aurelius", "Ethics" to "Baruch Spinoza")
            .map { (title, author) ->
                BookCandidate(
                    title = title,
                    authors = listOf(author),
                    publishedYear = 2006,
                    publisher = "Penguin Classics",
                    source = BookSource.GOOGLE,
                )
            }
        rule.setContent {
            OtiumTheme(darkTheme = true) {
                TitleSearchContent(
                    TitleSearchUiState(query = "stoic", results = results),
                    TitleSearchActions(),
                )
            }
        }
        rule.saveScreenshot("search-dark")
    }
}
