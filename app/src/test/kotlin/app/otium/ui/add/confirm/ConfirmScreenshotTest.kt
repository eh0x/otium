package app.otium.ui.add.confirm

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.otium.ui.saveScreenshot
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the check-the-details screen headlessly to build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class ConfirmScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val state = ConfirmUiState(
        form = BookForm(
            title = "Meditations",
            authors = "Marcus Aurelius",
            totalPages = "304",
            publisher = "Penguin Classics",
            publishedYear = "2006",
        ),
        isLoading = false,
    )

    private fun render(name: String, dark: Boolean) {
        rule.setContent { OtiumTheme(darkTheme = dark) { ConfirmContent(state, ConfirmActions()) } }
        rule.saveScreenshot(name)
    }

    @Test
    fun `confirm in the light theme`() = render("confirm-light", dark = false)

    @Test
    fun `confirm in the dark theme`() = render("confirm-dark", dark = true)
}
