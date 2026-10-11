package app.otium.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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

    private fun show(
        state: BackupUiState,
        actions: BackupActions = BackupActions(),
        encryption: EncryptionUiState = EncryptionUiState(),
        encryptionActions: EncryptionActions = EncryptionActions(),
    ) = rule.setContent {
        OtiumTheme(darkTheme = false) {
            BackupContent(
                state = state,
                actions = actions,
                encryption = encryption,
                encryptionActions = encryptionActions,
            )
        }
    }

    // --- Google Drive ---

    @Test
    fun `signed out, the user can connect but not back up or restore`() {
        var connected = 0
        show(BackupUiState(), BackupActions(onConnect = { connected++ }))

        rule.onNodeWithText("Back up now").assertIsNotEnabled()
        rule.onNodeWithText("Restore from Drive").assertIsNotEnabled()
        rule.onNodeWithText("Connect Google Drive to back up automatically.")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Connect").performScrollTo().performClick()

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

        // The button names the work while it runs.
        rule.onNodeWithText("Backing up…").assertIsDisplayed().assertIsNotEnabled()
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
        val preview = RestorePreview(BackupSlot.LATEST, backupInfo(bookCount = 12))
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
        val preview = RestorePreview(BackupSlot.LATEST, backupInfo())
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

        rule.onNodeWithText("Automatic backup").performScrollTo().performClick()

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

        rule.onNodeWithText("Automatic backup").performScrollTo().assertIsNotEnabled()
    }

    // --- Encryption ---

    @Test
    fun `with encryption off, it is recommended and a passphrase can be set`() {
        var asked = 0
        show(signedIn, encryptionActions = EncryptionActions(onSetPassphrase = { asked++ }))

        rule.onNodeWithText(
            "Off. With a passphrase only you can read your backups, even if someone enters " +
                "your Google account.",
        )
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Turn off").assertDoesNotExist()
        rule.onNodeWithText("Encrypt backups").performScrollTo().performClick()

        assertEquals(1, asked)
    }

    @Test
    fun `with encryption on, the passphrase can be changed or encryption turned off`() {
        var turnedOff = 0
        show(
            signedIn,
            encryption = EncryptionUiState(isOn = true),
            encryptionActions = EncryptionActions(onTurnOff = { turnedOff++ }),
        )

        rule.onNodeWithText("On. New backups are encrypted with your passphrase.")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Change passphrase").performScrollTo().assertIsEnabled()
        rule.onNodeWithText("Encrypt backups").performScrollTo().performClick()

        assertEquals(1, turnedOff)
    }

    @Test
    fun `the passphrase cannot be changed while a backup runs`() {
        show(
            signedIn.copy(busy = BackupBusy.BACKING_UP),
            encryption = EncryptionUiState(isOn = true),
        )

        rule.onNodeWithText("Change passphrase").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Encrypt backups").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `while the key is derived, progress is shown`() {
        show(signedIn, encryption = EncryptionUiState(isWorking = true))

        rule.onNodeWithText("Protecting your key…").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Encrypt backups").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `turning encryption off asks first`() {
        var confirmed = 0
        show(
            signedIn,
            encryption = EncryptionUiState(isOn = true, confirmTurnOff = true),
            encryptionActions = EncryptionActions(onTurnOffConfirmed = { confirmed++ }),
        )

        rule.onNodeWithText("Turn off encryption?").assertIsDisplayed()
        rule.onNodeWithText(
            "New backups will not be encrypted. Backups already in Drive stay encrypted and " +
                "still need your passphrase.",
        ).assertIsDisplayed()
        rule.onAllNodesWithText("Turn off").onLast().performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun `the last backup says when it is encrypted`() {
        show(signedIn.copy(lastBackup = backupInfo(keyId = "abc")))

        rule.onNodeWithText("Encrypted", substring = true).performScrollTo().assertIsDisplayed()
    }
}
