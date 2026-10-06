package app.otium.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.otium.domain.model.BookStatus
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(content: @Composable () -> Unit) =
        rule.setContent { OtiumTheme(darkTheme = false) { content() } }

    // --- StatusChip: status is never conveyed by color alone ---

    @Test
    fun `status chip shows a text label for every status`() {
        val labels = mapOf(
            BookStatus.NOT_STARTED to "Not started",
            BookStatus.IN_PROGRESS to "In progress",
            BookStatus.READ to "Read",
        )

        show {
            Column {
                labels.keys.forEach { status -> StatusChip(status) }
            }
        }

        labels.values.forEach { label -> rule.onNodeWithText(label).assertIsDisplayed() }
    }

    // --- OtiumProgressBar ---

    private fun progressInfo(): ProgressBarRangeInfo = rule
        .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        .fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo]

    @Test
    fun `progress bar exposes its fraction and a percentage description`() {
        show { OtiumProgressBar(progress = 0.42f) }

        assertEquals(0.42f, progressInfo().current, 0.001f)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "42%"))
            .assertIsDisplayed()
    }

    @Test
    fun `progress bar clamps out of range values`() {
        show { OtiumProgressBar(progress = 1.7f) }
        assertEquals(1f, progressInfo().current, 0.001f)
    }

    @Test
    fun `progress bar clamps negative values`() {
        show { OtiumProgressBar(progress = -0.3f) }
        assertEquals(0f, progressInfo().current, 0.001f)
    }

    // --- BookCover ---

    @Test
    fun `cover placeholder is described by the book title`() {
        show { BookCover(title = "The Hobbit") }

        rule.onNodeWithContentDescription("Cover of The Hobbit").assertIsDisplayed()
    }

    @Test
    fun `cover initials skip leading articles and use at most two letters`() {
        assertEquals("H", coverInitials("The Hobbit"))
        assertEquals("CA", coverInitials("Cien años de soledad"))
        assertEquals("GO", coverInitials("A Game of Thrones"))
        assertEquals("D", coverInitials("dune"))
        assertEquals("", coverInitials("   "))
    }

    // --- Layout components ---

    @Test
    fun `shelf row renders its books`() {
        show {
            ShelfRow {
                Text("First book")
                Text("Second book")
            }
        }

        rule.onNodeWithText("First book").assertIsDisplayed()
        rule.onNodeWithText("Second book").assertIsDisplayed()
    }

    @Test
    fun `top bar shows its title`() {
        show { OtiumTopBar(title = "Library") }

        rule.onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun `wordmark spells the app name in capitals`() {
        show { OtiumWordmark() }

        rule.onNodeWithText("OTIUM").assertIsDisplayed()
    }
}
