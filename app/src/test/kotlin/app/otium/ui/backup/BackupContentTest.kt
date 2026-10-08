package app.otium.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupFrequency
import app.otium.domain.model.BackupSettings
import app.otium.domain.model.BackupSlot
import app.otium.domain.model.DriveAuthState
import app.otium.domain.model.RestorePreview
import app.otium.ui.theme.OtiumTheme
import java.io.File
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
class BackupContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val signedIn = BackupUiState(auth = DriveAuthState.SignedIn("reader@example.org"))

    private fun show(state: BackupUiState, actions: BackupActions = BackupActions()) =
        rule.setContent { OtiumTheme(darkTheme = false) { BackupContent(state, actions) } }

    // --- Google Drive ---

    @Test
    fun `signed out, the user can connect but not back up or restore`() {
        var connected = 0
        show(BackupUiState(), BackupActions(onConnect = { connected++ }))

        rule.onNodeWithText("Back up now").assertIsNotEnabled()
        rule.onNodeWithText("Restore from Drive").assertIsNotEnabled()
        rule.onNodeWithText("Connect Google Drive to back up automatically.").assertIsDisplayed()
        rule.onNodeWithText("Connect Google Drive").performClick()

        assertEquals(1, connected)
    }

    @Test
    fun `signed in, the account is shown and backing up is possible`() {
        var backedUp = 0
        show(signedIn, BackupActions(onBackUpNow = { backedUp++ }))

        rule.onNodeWithText("Connected as reader@example.org").assertIsDisplayed()
        rule.onNodeWithText("Back up now").assertIsEnabled().performClick()

        assertEquals(1, backedUp)
    }

    @Test
    fun `an account that is not known still shows as connected`() {
        show(BackupUiState(auth = DriveAuthState.SignedIn(account = null)))

        rule.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun `an expired sign-in asks to sign in again`() {
        var connected = 0
        show(
            BackupUiState(auth = DriveAuthState.Expired),
            BackupActions(onConnect = { connected++ }),
        )

        rule.onNodeWithText("Your Google sign-in expired.").assertIsDisplayed()
        rule.onNodeWithText("Sign in again").performClick()

        assertEquals(1, connected)
    }

    @Test
    fun `the user can disconnect`() {
        var disconnected = 0
        show(signedIn, BackupActions(onDisconnect = { disconnected++ }))

        rule.onNodeWithText("Disconnect").performClick()

        assertEquals(1, disconnected)
    }

    // --- Backing up ---

    @Test
    fun `before the first backup the screen says so`() {
        show(signedIn)

        rule.onNodeWithText("No backup yet").assertIsDisplayed()
    }

    @Test
    fun `the last backup shows its books`() {
        show(signedIn.copy(lastBackup = backupInfo(bookCount = 3, sizeBytes = 2_048L)))

        rule.onNodeWithText("3 books", substring = true).assertIsDisplayed()
    }

    @Test
    fun `while backing up the buttons wait`() {
        show(signedIn.copy(busy = BackupBusy.BACKING_UP))

        rule.onNodeWithText("Backing up…").assertIsDisplayed()
        rule.onNodeWithText("Back up now").assertIsNotEnabled()
        rule.onNodeWithText("Restore from Drive").assertIsNotEnabled()
    }

    // --- Restoring ---

    @Test
    fun `restore asks for the latest backup, or the previous one`() {
        val asked = mutableListOf<BackupSlot>()
        show(signedIn, BackupActions(onRestore = { asked += it }))

        rule.onNodeWithText("Restore from Drive").performScrollTo().performClick()
        rule.onNodeWithText("Restore the previous backup").performScrollTo().performClick()

        assertEquals(listOf(BackupSlot.LATEST, BackupSlot.PREVIOUS), asked)
    }

    @Test
    fun `the confirm dialog says what will replace the library`() {
        var confirmed = 0
        val preview = RestorePreview(BackupSlot.LATEST, backupInfo(bookCount = 12), File("x"))
        show(
            signedIn.copy(restorePreview = preview),
            BackupActions(onRestoreConfirmed = { confirmed++ }),
        )

        rule.onNodeWithText("Replace your library?").assertIsDisplayed()
        rule.onNodeWithText("12 books", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Replace library").performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun `cancelling the confirm dialog reports it`() {
        var dismissed = 0
        val preview = RestorePreview(BackupSlot.LATEST, backupInfo(), File("x"))
        show(
            signedIn.copy(restorePreview = preview),
            BackupActions(onRestoreDismissed = { dismissed++ }),
        )

        rule.onNodeWithText("Cancel").performClick()

        assertEquals(1, dismissed)
    }

    // --- Automatic backup ---

    @Test
    fun `turning on automatic backup reports the new settings`() {
        val changes = mutableListOf<BackupSettings>()
        show(signedIn, BackupActions(onSettingsChanged = { changes += it }))

        rule.onNodeWithText("Back up automatically").performScrollTo().performClick()

        assertEquals(listOf(BackupSettings(autoBackup = true)), changes)
    }

    @Test
    fun `the options appear only while automatic backup is on`() {
        show(signedIn)

        rule.onNodeWithText("Only on Wi-Fi").assertDoesNotExist()
    }

    @Test
    fun `with automatic backup on, the frequency and conditions can be changed`() {
        val changes = mutableListOf<BackupSettings>()
        val on = BackupSettings(autoBackup = true)
        show(signedIn.copy(settings = on), BackupActions(onSettingsChanged = { changes += it }))

        rule.onNodeWithText("Daily").performScrollTo().performClick()
        rule.onNodeWithText("Only on Wi-Fi").performScrollTo().performClick()
        rule.onNodeWithText("Only while charging").performScrollTo().performClick()

        assertEquals(
            listOf(
                on.copy(frequency = BackupFrequency.DAILY),
                on.copy(wifiOnly = false),
                on.copy(chargingOnly = true),
            ),
            changes,
        )
    }

    @Test
    fun `automatic backup cannot be changed while signed out`() {
        show(BackupUiState())

        rule.onNodeWithText("Back up automatically").performScrollTo().assertIsNotEnabled()
    }
}
