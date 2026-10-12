package app.otium.ui.applock

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.otium.ui.saveScreenshot
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the lock screen, headlessly to build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class AppLockScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(name: String, dark: Boolean) {
        rule.setContent { OtiumTheme(darkTheme = dark) { AppLockScreen(onUnlock = {}) } }
        rule.saveScreenshot(name)
    }

    @Test
    fun `the lock screen in the light theme`() = render("app-lock-light", dark = false)

    @Test
    fun `the lock screen in the dark theme`() = render("app-lock-dark", dark = true)
}
