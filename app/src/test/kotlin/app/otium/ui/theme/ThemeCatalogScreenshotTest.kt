package app.otium.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.otium.ui.catalog.ThemeCatalogScreen
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the theme catalog headlessly in both themes and writes PNGs to
 * build/screenshots for review. Also guards against a blank or uniform render.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class ThemeCatalogScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private fun render(dark: Boolean, name: String) {
        rule.setContent { OtiumTheme(darkTheme = dark) { ThemeCatalogScreen() } }
        rule.waitForIdle()

        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        val distinctColors = (0 until bitmap.height step SAMPLE_STEP).flatMap { y ->
            (0 until bitmap.width step SAMPLE_STEP).map { x -> bitmap.getPixel(x, y) }
        }.toSet().size
        assertTrue("$name render looks blank ($distinctColors colors)", distinctColors > MIN_COLORS)
    }

    @Test
    fun `catalog renders in the light theme`() = render(dark = false, name = "theme-catalog-light")

    @Test
    fun `catalog renders in the dark theme`() = render(dark = true, name = "theme-catalog-dark")

    private companion object {
        const val SAMPLE_STEP = 7
        const val MIN_COLORS = 25
    }
}
