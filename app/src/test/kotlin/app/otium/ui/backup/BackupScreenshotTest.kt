package app.otium.ui.backup

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.otium.domain.fakes.backupInfo
import app.otium.domain.model.BackupSettings
import app.otium.domain.model.DriveAuthState
import app.otium.ui.saveScreenshot
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders Settings, Backup and restore, headlessly to build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h1400dp-xxhdpi")
class BackupScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val state = BackupUiState(
        auth = DriveAuthState.SignedIn("reader@example.org"),
        lastBackup = backupInfo(keyId = "abc").copy(createdAt = OCTOBER_9_2026),
        settings = BackupSettings(autoBackup = true),
    )

    private fun render(name: String, dark: Boolean) {
        rule.setContent {
            OtiumTheme(darkTheme = dark) {
                BackupContent(
                    state = state,
                    actions = BackupActions(),
                    encryption = EncryptionUiState(isOn = true),
                )
            }
        }
        rule.saveScreenshot(name)
    }

    @Test
    fun `settings in the light theme`() = render("settings-light", dark = false)

    @Test
    fun `settings in the dark theme`() = render("settings-dark", dark = true)

    private companion object {
        /** 9 October 2026, 09:12 UTC. */
        const val OCTOBER_9_2026 = 1_791_537_120_000L
    }
}
