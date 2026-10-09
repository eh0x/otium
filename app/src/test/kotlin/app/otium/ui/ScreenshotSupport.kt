package app.otium.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import java.io.File
import org.junit.Assert.assertTrue

private const val SAMPLE_STEP = 7
private const val MIN_COLORS = 25

/**
 * Captures what [rule] shows to build/screenshots/[name].png for review, so layout problems
 * (overflow, clipping, unreadable dark text) are seen rather than guessed at. Fails on a blank
 * or uniform render.
 */
fun ComposeContentTestRule.saveScreenshot(name: String) {
    waitForIdle()
    val bitmap = onRoot().captureToImage().asAndroidBitmap()
    val directory = File("build/screenshots").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    val distinctColors = (0 until bitmap.height step SAMPLE_STEP).flatMap { y ->
        (0 until bitmap.width step SAMPLE_STEP).map { x -> bitmap.getPixel(x, y) }
    }.toSet().size
    assertTrue("$name render looks blank ($distinctColors colors)", distinctColors > MIN_COLORS)
}
