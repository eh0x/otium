package app.otium.ui.add.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
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
class TitleSearchContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val dune = BookCandidate(
        title = "Dune",
        authors = listOf("Frank Herbert"),
        publishedYear = 1965,
        publisher = "Chilton",
        source = BookSource.GOOGLE,
    )

    private val chosen = mutableListOf<BookCandidate>()
    private var searches = 0
    private var manual = 0

    private fun show(state: TitleSearchUiState) = rule.setContent {
        OtiumTheme(darkTheme = false) {
            TitleSearchContent(
                state,
                TitleSearchActions(
                    onSearch = { searches++ },
                    onResultChosen = { chosen += it },
                    onEnterManually = { manual++ },
                ),
            )
        }
    }

    @Test
    fun `the keyboard search key searches`() {
        show(TitleSearchUiState(query = "dune"))

        rule.onNodeWithText("dune").performImeAction()

        assertEquals(1, searches)
    }

    @Test
    fun `an edition shows what tells it apart and can be chosen`() {
        show(TitleSearchUiState(query = "dune", results = listOf(dune)))

        rule.onNodeWithContentDescription(
            "Dune by Frank Herbert",
        ).assertIsDisplayed().performClick()

        assertEquals(listOf(dune), chosen)
    }

    @Test
    fun `no results suggest typing the details`() {
        show(TitleSearchUiState(query = "zzz", noResults = true))

        rule.onNodeWithText("No editions found. Try other words, or type the details yourself.")
            .assertIsDisplayed()
        rule.onNodeWithText("Type the details yourself").performClick()

        assertEquals(1, manual)
    }
}
