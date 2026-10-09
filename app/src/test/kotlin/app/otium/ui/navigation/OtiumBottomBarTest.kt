package app.otium.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OtiumBottomBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val selected = mutableListOf<TopLevelDestination>()
    private var adds = 0

    private fun show(current: TopLevelDestination) = rule.setContent {
        OtiumTheme(darkTheme = false) {
            OtiumBottomBar(selected = current, onSelect = { selected += it }, onAdd = { adds++ })
        }
    }

    @Test
    fun `the current tab is selected and the others are not`() {
        show(TopLevelDestination.READING)

        rule.onNodeWithText("Reading").assertIsSelected()
        rule.onNodeWithText("Library").assertIsNotSelected()
        rule.onNodeWithText("Settings").assertIsNotSelected()
    }

    @Test
    fun `tapping a tab reports it`() {
        show(TopLevelDestination.LIBRARY)

        rule.onNodeWithText("Settings").performClick()

        assertEquals(listOf(TopLevelDestination.SETTINGS), selected)
    }

    @Test
    fun `the add action is described and opens the add sheet`() {
        show(TopLevelDestination.LIBRARY)

        rule.onNodeWithContentDescription("Add a book").assertIsDisplayed().performClick()

        assertEquals(1, adds)
    }
}
