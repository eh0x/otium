package app.otium.ui.add.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.otium.domain.error.AppError
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
class ScanContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val pressed = mutableListOf<String>()
    private val actions = ScanActions(
        onEnterIsbn = { pressed += "isbn" },
        onAllowCamera = { pressed += "allow" },
        onOpenSettings = { pressed += "settings" },
        onScanAgain = { pressed += "again" },
        onSaveForLater = { pressed += "later" },
        onEnterManually = { pressed += "manual" },
    )

    private fun show(state: ScanUiState, access: CameraAccess = CameraAccess.GRANTED) =
        rule.setContent {
            OtiumTheme(darkTheme = false) {
                ScanContent(state, actions, access) { analyzing ->
                    Box(Modifier.semantics { contentDescription = "camera analyzing=$analyzing" })
                }
            }
        }

    @Test
    fun `while scanning the camera decodes and the user is guided`() {
        show(ScanUiState())

        rule.onNodeWithContentDescription("camera analyzing=true").assertExists()
        rule.onNodeWithText(
            "Hold the barcode on the back cover inside the frame",
        ).assertIsDisplayed()
        rule.onNodeWithText("Enter the ISBN instead").performClick()

        assertEquals(listOf("isbn"), pressed)
    }

    @Test
    fun `after a read decoding stops while the book is looked up`() {
        show(ScanUiState(phase = ScanPhase.LOOKING_UP, reads = 1, isbn13 = "9780441013593"))

        rule.onNodeWithContentDescription("camera analyzing=false").assertExists()
        rule.onNodeWithText("Looking up 9780441013593").assertIsDisplayed()
    }

    @Test
    fun `a failed lookup offers every way out`() {
        show(
            ScanUiState(
                phase = ScanPhase.FAILED,
                isbn13 = "9780441013593",
                error = AppError.Offline,
            ),
        )

        rule.onNodeWithText("You are offline. Check your connection and try again.")
            .assertIsDisplayed()
        rule.onNodeWithText("Scan again").performClick()
        rule.onNodeWithText("Save the ISBN, fill in details later").performClick()
        rule.onNodeWithText("Type the details yourself").performClick()

        assertEquals(listOf("again", "later", "manual"), pressed)
    }

    @Test
    fun `without permission the reason is given and access can be asked for`() {
        show(ScanUiState(), CameraAccess.ASK)

        rule.onNodeWithText(
            "Otium uses the camera only to read the barcode on a book. " +
                "Nothing is recorded or sent anywhere.",
        ).assertIsDisplayed()
        rule.onNodeWithText("Allow camera").performClick()
        rule.onNodeWithText("Enter the ISBN instead").performClick()

        assertEquals(listOf("allow", "isbn"), pressed)
    }

    @Test
    fun `refused for good points to the settings`() {
        show(ScanUiState(), CameraAccess.BLOCKED)

        rule.onNodeWithText("Open settings").performClick()

        assertEquals(listOf("settings"), pressed)
    }

    @Test
    fun `no camera leaves the other ways to add`() {
        show(ScanUiState(), CameraAccess.NO_CAMERA)

        rule.onNodeWithText("This device has no camera. You can still add books by ISBN or title.")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("camera analyzing=true").assertDoesNotExist()
    }
}
