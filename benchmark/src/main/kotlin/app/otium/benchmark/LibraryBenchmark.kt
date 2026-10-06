package app.otium.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "app.otium"
private const val ITERATIONS = 5
private const val FLINGS_PER_DIRECTION = 3
private const val GESTURE_MARGIN_DIVISOR = 5
private const val SEED_TIMEOUT_MILLIS = 180_000L
private const val UI_TIMEOUT_MILLIS = 5_000L
private const val SEARCH_TEXT = "an"

// The search text is debounced by about 200 ms before the list updates.
private const val SEARCH_SETTLE_MILLIS = 600L

/** The seeded library: 5,000 books, formatted with or without a thousands separator. */
private val SEEDED_COUNT: Pattern = Pattern.compile("5\\D?000 books")

/**
 * Frame timing for the two things that must stay smooth with 5,000 books: scrolling the shelf and
 * filtering by search text. Run on a device or emulator with
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest`. Emulator numbers are for comparison
 * between runs only; use a real mid-range device for the figures in the docs.
 */
@RunWith(AndroidJUnit4::class)
class LibraryBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollShelf() = measure {
        val shelf =
            device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() }
                ?: error("No scrollable shelf found")
        shelf.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
        repeat(FLINGS_PER_DIRECTION) { shelf.fling(Direction.DOWN) }
        repeat(FLINGS_PER_DIRECTION) { shelf.fling(Direction.UP) }
    }

    /** Typing narrows 5,000 books to a few hundred. */
    @Test
    fun searchTyping() = measure(prepare = { setSearchText("") }) {
        setSearchText(SEARCH_TEXT)
    }

    /** Clearing the search brings all 5,000 books back at once. */
    @Test
    fun searchClearing() = measure(prepare = { setSearchText(SEARCH_TEXT) }) {
        setSearchText("")
    }

    private fun measure(
        prepare: MacrobenchmarkScope.() -> Unit = {},
        block: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            seedLibraryIfEmpty()
            prepare()
        },
        measureBlock = block,
    )

    /** Sets the search text and waits out the debounce, so the list has settled on return. */
    private fun MacrobenchmarkScope.setSearchText(text: String) {
        val field =
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), UI_TIMEOUT_MILLIS)
                ?: error("No search field found")
        field.click()
        field.text = text
        Thread.sleep(SEARCH_SETTLE_MILLIS)
    }

    /** The seeder skips books it already added, so this is cheap after the first run. */
    private fun MacrobenchmarkScope.seedLibraryIfEmpty() {
        if (device.hasObject(By.text(SEEDED_COUNT))) return
        device.findObject(By.desc("More options")).click()
        device.wait(Until.findObject(By.textStartsWith("Seed ")), UI_TIMEOUT_MILLIS)?.click()
            ?: error("Seed action not found; is this the benchmark build?")
        check(device.wait(Until.hasObject(By.text(SEEDED_COUNT)), SEED_TIMEOUT_MILLIS)) {
            "The library did not reach 5,000 books in time"
        }
    }
}
