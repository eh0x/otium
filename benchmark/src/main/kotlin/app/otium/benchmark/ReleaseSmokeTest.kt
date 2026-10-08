package app.otium.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.textAsString
import androidx.test.uiautomator.uiAutomator
import org.junit.Test
import org.junit.runner.RunWith

private const val APP = "app.otium"
private const val NETWORK_TIMEOUT_MILLIS = 20_000L

// Search is debounced by about 200 ms; give the list time to settle before checking it.
private const val LIST_SETTLE_MILLIS = 1_500L

/**
 * Black-box smoke test of the shrunk build: the `benchmark` build type is the release build (R8,
 * resource shrinking) signed with the debug key. It catches what unit tests cannot: a class R8
 * removed or renamed that Room, Hilt, navigation routes or kotlinx.serialization reach by name.
 *
 * Run with `./gradlew :benchmark:connectedBenchmarkAndroidTest` on a device or emulator. Each run
 * adds a book with a unique title and deletes it again, so it leaves the library as it found it.
 * Drive backup and restore need a Google account and stay a manual check.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {

    /** Room, Hilt, typed navigation and DataStore: add by hand, find, track progress, delete. */
    @Test
    fun addTrackAndDeleteABookByHand() = uiAutomator {
        val title = "Smoke ${System.currentTimeMillis()}"
        startApp(APP)
        waitForAppToBeVisible(APP)

        tap("Add book")
        tap("Enter ISBN")
        tap("Type the details yourself")
        onElement { textAsString() == "Check the details" }
        enter(title)
        tap("Save")

        // Saving opens the new book.
        onElement { textAsString() == title }
        tap("Set total pages")
        enter("300")
        tap("Save")
        tap("Update progress")
        enter("150")
        tap("Save")
        onElement { textAsString() == "Page 150 of 300" }

        tap("More options")
        tap("Delete book")
        tap("Delete")

        // Back in the library: searching for the title finds no book, whatever else is shelved.
        enter(title)
        waitForStableInActiveWindow()
        val stillListed = onElementOrNull(LIST_SETTLE_MILLIS) {
            !isEditable && textAsString() == title
        }
        check(stillListed == null) { "\"$title\" is still in the library after deleting it" }
    }

    /** Retrofit, OkHttp and the kotlinx.serialization DTOs: a real ISBN lookup. Needs a network. */
    @Test
    fun lookUpAnIsbnOnline() = uiAutomator {
        startApp(APP)
        waitForAppToBeVisible(APP)

        tap("Add book")
        tap("Enter ISBN")
        enter(PRIDE_AND_PREJUDICE)
        tap("Look up")

        onElement(NETWORK_TIMEOUT_MILLIS) { textAsString() == "Check the details" }
        onElement { textAsString()?.contains("Pride", ignoreCase = true) == true }
    }

    /**
     * Taps what shows [label] as text or content description. Sheets and screens animate in, so
     * it waits for the window to settle first; otherwise the tap lands where the label was.
     */
    private fun UiAutomatorTestScope.tap(label: String) {
        waitForStableInActiveWindow()
        onElement { textAsString() == label || contentDescription?.toString() == label }.click()
    }

    /** Types [text] into the first text field on screen, once the screen has settled. */
    private fun UiAutomatorTestScope.enter(text: String) {
        waitForStableInActiveWindow()
        onElement { isEditable && isVisibleToUser }.setText(text)
    }

    private companion object {
        /** Penguin Classics edition; known to both Google Books and Open Library. */
        const val PRIDE_AND_PREJUDICE = "9780141439518"
    }
}
