package app.otium.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontWeight
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
        assertEquals(Color(LightPalette.surface), theme.colors.surface)
        assertEquals(Color(LightPalette.sheet), theme.colors.surfaceContainerLow)
        assertEquals(Color(LightPalette.sheet), theme.colors.surfaceContainerHigh)
        assertEquals(Color(LightPalette.muted), theme.colors.outline)
        assertEquals(Color(LightPalette.divider), theme.colors.outlineVariant)
    }

    @Test
    fun `dark theme uses the dark palette`() {
        val theme = capture(dark = true)

        assertEquals(Color(DarkPalette.background), theme.colors.background)
        assertEquals(Color(DarkPalette.primary), theme.colors.primary)
        assertNotEquals(Color(LightPalette.background), theme.colors.background)
    }

    @Test
    fun `there is one accent hue and no second one`() {
        val theme = capture(dark = false)

        assertEquals(Color(LightPalette.onBackground), theme.colors.secondary)
        assertEquals(Color(LightPalette.muted), theme.colors.tertiary)
        assertEquals(Color(LightPalette.accent), theme.otium.accent)
    }

    @Test
    fun `status colors are muted when not started and accent once begun`() {
        val theme = capture(dark = false)

        assertEquals(theme.otium.muted, theme.otium.statusNotStarted)
        assertEquals(theme.otium.accent, theme.otium.statusInProgress)
        assertEquals(theme.otium.accent, theme.otium.statusRead)
        assertNotEquals(theme.otium.statusNotStarted, theme.otium.statusInProgress)
    }

    @Test
    fun `display and controls use Cormorant and reading text uses Lora`() {
        val typography = capture(dark = false).typography

        assertEquals(OtiumFonts.Display, typography.displayLarge.fontFamily)
        assertEquals(OtiumFonts.Display, typography.headlineMedium.fontFamily)
        assertEquals(OtiumFonts.Display, typography.titleSmall.fontFamily)
        assertEquals(OtiumFonts.Display, typography.labelLarge.fontFamily)
        assertEquals(OtiumFonts.Text, typography.bodyLarge.fontFamily)
        assertEquals(OtiumFonts.Text, typography.labelSmall.fontFamily)
    }

    @Test
    fun `Cormorant is never regular weight below 28 sp`() {
        val typography = capture(dark = false).typography
        val styles = with(typography) {
            listOf(
                displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium,
                headlineSmall, titleLarge, titleMedium, titleSmall, labelLarge, labelMedium,
            )
        }

        styles.filter { it.fontSize.value < MIN_REGULAR_CORMORANT_SP }.forEach {
            assertEquals(FontWeight.SemiBold, it.fontWeight)
        }
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

    private companion object {
        const val MIN_REGULAR_CORMORANT_SP = 28f
    }
}
