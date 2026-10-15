package app.otium.ui.applock

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.otium.applock.LockState
import app.otium.domain.fakes.book
import app.otium.ui.components.OtiumDialog
import app.otium.ui.components.OtiumSheet
import app.otium.ui.detail.DetailActions
import app.otium.ui.detail.DetailContent
import app.otium.ui.detail.DetailUiState
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dialogs, sheets and menus are windows of their own: hiding the screen below them does not hide
 * them. While the lock is on, none of them may be on screen, whatever was open when it engaged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp")
class AppLockWindowsTest {

    @get:Rule
    val rule = createComposeRule()

    private var lock by mutableStateOf(LockState.UNLOCKED)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent {
        OtiumTheme(darkTheme = false) {
            AppLockGate(
                gate = AppLockGateState(lock, 0, false, null),
                onUnlock = {},
                onNoticeShown = {},
                content = content,
            )
        }
    }

    @Test
    fun `a dialog open when the lock engages leaves the screen and returns after unlock`() {
        show {
            OtiumDialog(title = "A private draft", onDismissRequest = {}, actions = {}) {
                Text("Half a quote")
            }
        }
        rule.onNodeWithText("Half a quote").assertIsDisplayed()

        lock = LockState.LOCKED
        rule.waitForIdle()
        rule.onNodeWithText("Half a quote").assertDoesNotExist()
        rule.onNodeWithText("A private draft").assertDoesNotExist()

        lock = LockState.UNLOCKED
        rule.waitForIdle()
        rule.onNodeWithText("Half a quote").assertIsDisplayed()
    }

    @Test
    fun `a sheet open when the lock engages leaves the screen`() {
        show { OtiumSheet(onDismissRequest = {}) { Text("Library filters") } }
        rule.onNodeWithText("Library filters").assertIsDisplayed()

        lock = LockState.LOCKED
        rule.waitForIdle()

        rule.onNodeWithText("Library filters").assertDoesNotExist()
    }

    @Test
    fun `a menu open when the lock engages leaves the screen`() {
        val state = DetailUiState(isLoading = false, book = book(title = "Dune"))
        show { DetailContent(state, DetailActions()) }
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Delete book").assertIsDisplayed()

        lock = LockState.LOCKED
        rule.waitForIdle()

        rule.onNodeWithText("Delete book").assertDoesNotExist()
    }
}
