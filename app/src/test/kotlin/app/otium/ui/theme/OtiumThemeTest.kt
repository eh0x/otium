package app.otium.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OtiumThemeTest {

    @get:Rule
    val rule = createComposeRule()

    private data class Captured(
        val colors: ColorScheme,
        val otium: OtiumColors,
        val typography: Typography,
    )

    private fun capture(dark: Boolean): Captured {
        lateinit var captured: Captured
        rule.setContent {
            OtiumTheme(darkTheme = dark) {
                captured = Captured(
                    MaterialTheme.colorScheme,
                    MaterialTheme.otiumColors,
                    MaterialTheme.typography,
                )
            }
        }
        rule.waitForIdle()
        return captured
    }

    @Test
    fun `light theme maps the palette onto the color scheme`() {
        val theme = capture(dark = false)

        assertEquals(Color(LightPalette.background), theme.colors.background)
        assertEquals(Color(LightPalette.onBackground), theme.colors.onBackground)
        assertEquals(Color(LightPalette.primary), theme.colors.primary)
        assertEquals(Color(LightPalette.secondary), theme.colors.secondary)
        assertEquals(Color(LightPalette.surface), theme.colors.surface)
    }

    @Test
    fun `dark theme uses the dark palette`() {
        val theme = capture(dark = true)

        assertEquals(Color(DarkPalette.background), theme.colors.background)
        assertEquals(Color(DarkPalette.primary), theme.colors.primary)
        assertNotEquals(Color(LightPalette.background), theme.colors.background)
    }

    @Test
    fun `status colors are Terra in progress and Sage when read`() {
        val theme = capture(dark = false)

        assertEquals(theme.colors.secondary, theme.otium.statusInProgress)
        assertEquals(theme.colors.primary, theme.otium.statusRead)
        assertNotEquals(theme.otium.statusNotStarted, theme.otium.statusInProgress)
        assertNotEquals(theme.otium.statusNotStarted, theme.otium.statusRead)
    }

    @Test
    fun `headlines use the serif family and interface text the sans family`() {
        val typography = capture(dark = false).typography

        assertEquals(OtiumFonts.Serif, typography.headlineMedium.fontFamily)
        assertEquals(OtiumFonts.Serif, typography.displaySmall.fontFamily)
        assertEquals(OtiumFonts.Sans, typography.bodyLarge.fontFamily)
        assertEquals(OtiumFonts.Sans, typography.labelMedium.fontFamily)
    }

    private fun contentColorOnPaper(dark: Boolean): Pair<Color, Color> {
        var onPaper: Color? = null
        var ink: Color? = null
        rule.setContent {
            OtiumTheme(darkTheme = dark) {
                ink = MaterialTheme.colorScheme.onBackground
                PaperBackground { onPaper = LocalContentColor.current }
            }
        }
        rule.waitForIdle()
        return checkNotNull(onPaper) to checkNotNull(ink)
    }

    @Test
    fun `text on the paper background uses the ink color in the light theme`() {
        val (onPaper, ink) = contentColorOnPaper(dark = false)

        assertEquals(ink, onPaper)
    }

    @Test
    fun `text on the paper background stays readable in the dark theme`() {
        val (onPaper, ink) = contentColorOnPaper(dark = true)

        assertEquals(ink, onPaper)
        assertEquals(Color(DarkPalette.onBackground), onPaper)
    }

    @Test
    fun `window background resources match the theme palette`() {
        fun colorIn(dir: String): String {
            val xml = File("src/main/res/$dir/colors.xml").readText()
            return Regex("""name="window_background">#([0-9A-Fa-f]{6})<""")
                .find(xml)!!.groupValues[1]
        }

        fun hex(argb: Long) = "%06X".format(argb and 0xFFFFFF)

        assertEquals(hex(LightPalette.background), colorIn("values").uppercase())
        assertEquals(hex(DarkPalette.background), colorIn("values-night").uppercase())
    }

    @Test
    fun `reduced motion is off by default`() {
        var reduced = true
        rule.setContent { reduced = rememberReducedMotion() }
        rule.waitForIdle()

        assertFalse(reduced)
    }

    @Test
    fun `reduced motion follows the system animator duration scale`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )

        var reduced = false
        rule.setContent { reduced = rememberReducedMotion() }
        rule.waitForIdle()

        assertTrue(reduced)
    }

    @Test
    fun `reduced motion rule treats only a zero scale as disabled animations`() {
        assertTrue(isMotionReduced(animatorDurationScale = 0f))
        assertFalse(isMotionReduced(animatorDurationScale = 1f))
        assertFalse(isMotionReduced(animatorDurationScale = 0.5f))
    }
}
