package app.otium.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.otium.domain.model.BackupSlot
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The passphrase prompt's content, outside its dialog window: under Robolectric a text field in a
 * dialog window never lets Compose go idle.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp")
class PassphrasePanelTest {

    @get:Rule
    val rule = createComposeRule()

    private val entered = mutableListOf<Pair<String, String>>()
    private var dismissed = 0
    private var prompt by mutableStateOf<PassphrasePrompt>(PassphrasePrompt.Set(changing = false))

    private fun show(shown: PassphrasePrompt, busy: Boolean = false) {
        prompt = shown
        rule.setContent {
            OtiumTheme(darkTheme = false) {
                PassphrasePanel(
                    prompt = prompt,
                    busy = busy,
                    onSubmit = { p, c -> entered += String(p) to String(c) },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    private fun typeNewPassphrase(text: String) {
        rule.onNodeWithText("Passphrase").performTextInput(text)
        rule.onNodeWithText("Type it again").performTextInput(text)
        rule.onNodeWithText("I understand that a forgotten passphrase cannot be recovered")
            .performScrollTo()
            .performClick()
    }

    @Test
    fun `a new passphrase needs both fields and the warning acknowledged`() {
        show(PassphrasePrompt.Set(changing = false))

        rule.onNodeWithText("Set a backup passphrase").assertIsDisplayed()
        rule.onNodeWithText("Turn on encryption").assertIsNotEnabled()
        rule.onNodeWithText("Passphrase").performTextInput("correct horse")
        rule.onNodeWithText("Type it again").performTextInput("correct horse")
        rule.onNodeWithText("Turn on encryption").assertIsNotEnabled()
        rule.onNodeWithText("I understand that a forgotten passphrase cannot be recovered")
            .performScrollTo()
            .performClick()
        rule.onNodeWithText("Turn on encryption").assertIsEnabled().performClick()

        assertEquals(listOf("correct horse" to "correct horse"), entered)
    }

    @Test
    fun `the warning and the minimum length are spelled out`() {
        show(PassphrasePrompt.Set(changing = false))

        rule.onNodeWithText(
            "If you forget this passphrase, your encrypted backups cannot be restored. Otium " +
                "does not keep it and cannot recover it.",
        ).assertIsDisplayed()
        rule.onNodeWithText(
            "At least 8 characters. A few unrelated words are easy to remember and hard to guess.",
        ).assertIsDisplayed()
    }

    @Test
    fun `a passphrase that is too short says so`() {
        show(PassphrasePrompt.Set(changing = false, problem = PassphraseProblem.TOO_SHORT))

        rule.onNodeWithText("Use at least 8 characters.").assertIsDisplayed()
    }

    @Test
    fun `a mismatch says so, and changing warns about older backups`() {
        show(PassphrasePrompt.Set(changing = true, problem = PassphraseProblem.MISMATCH))

        rule.onNodeWithText("Change the backup passphrase").assertIsDisplayed()
        rule.onNodeWithText("The two passphrases do not match.")
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithText("Backups made before the change still need the old passphrase.")
            .assertIsDisplayed()
    }

    @Test
    fun `what was typed is cleared when the prompt comes back with a problem`() {
        show(PassphrasePrompt.Set(changing = false))
        typeNewPassphrase("short")
        rule.onNodeWithText("Turn on encryption").assertIsEnabled()

        prompt = PassphrasePrompt.Set(changing = false, problem = PassphraseProblem.TOO_SHORT)
        rule.waitForIdle()

        rule.onNodeWithText("Turn on encryption").assertIsNotEnabled()
    }

    @Test
    fun `the passphrase can be shown and hidden`() {
        show(PassphrasePrompt.Unlock(BackupSlot.LATEST))
        rule.onNodeWithText("Passphrase").performTextInput("correct horse")

        rule.onNodeWithContentDescription("Show passphrase").performClick()

        rule.onNodeWithText("correct horse").assertIsDisplayed()
        rule.onNodeWithContentDescription("Hide passphrase").assertIsDisplayed()
    }

    @Test
    fun `an encrypted backup asks for its passphrase and says when it is wrong`() {
        show(PassphrasePrompt.Unlock(BackupSlot.LATEST, wrong = true))

        rule.onNodeWithText("Enter the backup passphrase").assertIsDisplayed()
        rule.onNodeWithText("That passphrase does not open this backup.").assertIsDisplayed()
        rule.onNodeWithText("Unlock").assertIsNotEnabled()
        rule.onNodeWithText("Passphrase").performTextInput("another try")
        rule.onNodeWithText("Unlock").performClick()

        assertEquals(listOf("another try" to ""), entered)
    }

    @Test
    fun `while working, nothing can be submitted or cancelled`() {
        show(PassphrasePrompt.Unlock(BackupSlot.LATEST), busy = true)

        rule.onNodeWithText("Unlock").assertIsNotEnabled()
        rule.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun `cancel dismisses without submitting`() {
        show(PassphrasePrompt.Set(changing = false))

        rule.onNodeWithText("Cancel").performClick()

        assertEquals(1, dismissed)
        assertEquals(emptyList<Pair<String, String>>(), entered)
    }
}
