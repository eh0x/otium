package app.otium.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.otium.domain.model.BookStatus
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // --- StatusGlyph: status is never conveyed by color alone ---

    @Test
    fun `status glyph shows a text label for every status`() {
        val labels = mapOf(
            BookStatus.NOT_STARTED to "Unread",
            BookStatus.IN_PROGRESS to "Reading",
            BookStatus.READ to "Read",
        )

        show {
            Column {
                labels.keys.forEach { status -> StatusGlyph(status) }
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
    fun `progress bar with a ribbon keeps its progress semantics`() {
        show { OtiumProgressBar(progress = 0.5f, thickness = 3.dp, ribbon = true) }
        assertEquals(0.5f, progressInfo().current, 0.001f)
    }

    // --- BookCover ---

    @Test
    fun `generated cover is described by the book title`() {
        show { BookCover(title = "The Hobbit", authors = "J. R. R. Tolkien") }

        rule.onNodeWithContentDescription("Cover of The Hobbit").assertIsDisplayed()
    }

    @Test
    fun `plated cover is described by the book title`() {
        show { PlatedCover(title = "Ethics", authors = "Baruch Spinoza") }

        rule.onNodeWithContentDescription("Cover of Ethics").assertIsDisplayed()
    }

    @Test
    fun `cover surname is the last word of the first author`() {
        assertEquals("Aurelius", coverSurname("Marcus Aurelius"))
        assertEquals("Pratchett", coverSurname("Terry Pratchett, Neil Gaiman"))
        assertEquals("Homer", coverSurname("  Homer "))
        assertEquals("", coverSurname(null))
        assertEquals("", coverSurname(""))
    }

    @Test
    fun `cover board is stable per title and within range`() {
        val titles = listOf("Meditations", "Dune", "", "The Name of the Rose", "Ethics")

        titles.forEach { title ->
            val index = coverBoardIndex(title, BOARD_COUNT)
            assertTrue(index in 0 until BOARD_COUNT)
            assertEquals(index, coverBoardIndex(title, BOARD_COUNT))
            assertEquals(index, coverBoardIndex(" ${title.uppercase()} ", BOARD_COUNT))
        }
    }

    // --- Controls ---

    @Test
    fun `button runs its action when tapped`() {
        var clicks = 0
        show { OtiumButton("Mark as finished", { clicks++ }) }

        rule.onNodeWithText("Mark as finished").assertHasClickAction().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `disabled button is exposed as disabled and ignores taps`() {
        var clicks = 0
        show { OtiumButton("Back up now", { clicks++ }, enabled = false) }

        rule.onNode(hasText("Back up now") and hasClickAction()).assertIsNotEnabled().performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `chip exposes its selection to screen readers`() {
        show {
            Column {
                OtiumChip(label = "Reading", selected = true, onClick = {})
                OtiumChip(label = "Read", selected = false, onClick = {})
            }
        }

        rule.onNodeWithText("Reading").assertIsSelected()
        rule.onNodeWithText("Read").assertIsNotSelected()
    }

    @Test
    fun `switch is a toggleable switch that reports its state`() {
        var checked by mutableStateOf(false)
        show { OtiumSwitch(checked = checked, onCheckedChange = { checked = it }) }

        val switch = rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        )
        switch.assertIsOff().performClick()

        assertTrue(checked)
        switch.assertIsOn()
    }

    @Test
    fun `switch without a callback leaves the toggle to its row`() {
        show { OtiumSwitch(checked = true, onCheckedChange = null) }

        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .assertDoesNotExist()
    }

    @Test
    fun `dialog card shows its title as a heading, its body and its actions`() {
        show {
            OtiumDialogCard(
                title = "Delete this book?",
                body = "This cannot be undone.",
                actions = { OtiumButton("Delete", {}) },
            )
        }

        rule.onNode(hasText("Delete this book?") and isHeading()).assertIsDisplayed()
        rule.onNodeWithText("This cannot be undone.").assertIsDisplayed()
        rule.onNodeWithText("Delete").assertHasClickAction()
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

    private companion object {
        const val BOARD_COUNT = 12
    }
}
