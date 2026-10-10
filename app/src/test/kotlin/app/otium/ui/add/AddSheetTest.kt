package app.otium.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class AddSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val chosen = mutableListOf<AddMethod>()

    private fun show() = rule.setContent {
        OtiumTheme(darkTheme = false) {
            AddSheet(onChoose = { chosen += it }, onDismiss = {})
        }
    }

    @Test
    fun `offers the three ways to add a book`() {
        show()

        rule.onNodeWithText("Scan the barcode").assertIsDisplayed()
        rule.onNodeWithText("Enter an ISBN").assertIsDisplayed()
        rule.onNodeWithText("Search by title").assertIsDisplayed()
    }

    @Test
    fun `each option reports its method`() {
        show()

        rule.onNodeWithText("Enter an ISBN").performClick()

        assertEquals(listOf(AddMethod.ISBN), chosen)
    }
}
