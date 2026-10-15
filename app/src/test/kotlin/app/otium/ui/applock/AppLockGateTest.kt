package app.otium.ui.applock

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.otium.applock.LockState
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
class AppLockGateTest {

    @get:Rule
    val rule = createComposeRule()

    private var unlocks = 0
    private var noticesShown = 0

    private fun show(
        state: LockState,
        promptRequest: Int = 0,
        turnedOffNotice: Boolean = false,
        message: String? = null,
    ) = rule.setContent {
        OtiumTheme(darkTheme = false) {
            AppLockGate(
                gate = AppLockGateState(state, promptRequest, turnedOffNotice, message),
                onUnlock = { unlocks++ },
                onNoticeShown = { noticesShown++ },
            ) {
                Text("The Hobbit")
            }
        }
    }

    @Test
    fun `unlocked, the library shows`() {
        show(LockState.UNLOCKED)

        rule.onNodeWithText("The Hobbit").assertIsDisplayed()
        rule.onNodeWithText("Otium is locked").assertDoesNotExist()
    }

    @Test
    fun `locked, nothing of the library can be read, even by a screen reader`() {
        show(LockState.LOCKED)

        rule.onNodeWithText("Otium is locked").assertIsDisplayed()
        rule.onNodeWithText("The Hobbit").assertDoesNotExist()
    }

    @Test
    fun `while the choice is being read, nothing of the library shows either`() {
        show(LockState.CHECKING)

        rule.onNodeWithText("The Hobbit").assertDoesNotExist()
        rule.onNodeWithText("Otium is locked").assertDoesNotExist()
    }

    @Test
    fun `unlock asks for the owner`() {
        show(LockState.LOCKED)

        rule.onNodeWithText("Unlock").performClick()

        assertEquals(1, unlocks)
    }

    @Test
    fun `the prompt opens by itself once per request, not again after a cancel`() {
        var request by mutableStateOf(1)
        rule.setContent {
            OtiumTheme(darkTheme = false) {
                AppLockGate(
                    gate = AppLockGateState(LockState.LOCKED, promptRequest = request),
                    onUnlock = { unlocks++ },
                    onNoticeShown = {},
                ) { Text("The Hobbit") }
            }
        }
        rule.waitForIdle()
        assertEquals(1, unlocks)

        // Recomposing (as after a cancelled prompt) does not ask again.
        rule.waitForIdle()
        assertEquals(1, unlocks)

        request = 2
        rule.waitForIdle()
        assertEquals(2, unlocks)
    }

    @Test
    fun `a refusal from the system is shown on the lock screen`() {
        show(LockState.LOCKED, message = "Too many attempts. Try again later.")

        rule.onNodeWithText("Too many attempts. Try again later.").assertIsDisplayed()
    }

    @Test
    fun `the lock that turned itself off says so once`() {
        show(LockState.UNLOCKED, turnedOffNotice = true)

        rule.onNodeWithText("App lock turned off").assertIsDisplayed()
        rule.onNodeWithText("OK").performClick()

        assertEquals(1, noticesShown)
    }
}
