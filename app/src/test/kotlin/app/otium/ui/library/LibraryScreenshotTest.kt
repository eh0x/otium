package app.otium.ui.library

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortKeys
import app.otium.domain.usecase.DeriveStatus
import app.otium.ui.theme.OtiumTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the library headlessly and writes PNGs to build/screenshots for review, so layout
 * problems (overflow, clipping, unreadable dark text) are seen rather than guessed at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class LibraryScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val titles = listOf(
        "Dune" to "Frank Herbert",
        "The Left Hand of Darkness" to "Ursula K. Le Guin",
        "Emma" to "Jane Austen",
        "A Wizard of Earthsea" to "Ursula K. Le Guin",
        "Piranesi" to "Susanna Clarke",
        "The Name of the Rose" to "Umberto Eco",
    )

    private val books = titles.mapIndexed { index, (title, author) ->
        val total = 300
        val current = listOf(0, 120, 300, 45, 0, 210)[index]
        BookSummary(
            id = index + 1L,
            title = title,
            authors = author,
            coverPath = null,
            coverSourceUrl = null,
            currentPage = current,
            totalPages = total,
            status = DeriveStatus(current, total),
            shelf = listOf("Fiction", "Fantasy", "Classics", "Fantasy", "Fiction", null)[index],
            updatedAt = index.toLong(),
        )
    }.sortedWith(SortKeys.byTitle) // As the repository returns them.

    private fun render(name: String, dark: Boolean, state: LibraryUiState) {
        rule.setContent { OtiumTheme(darkTheme = dark) { LibraryContent(state, LibraryActions()) } }
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

    private fun state(mode: LibraryViewMode) = LibraryUiState(
        books = books,
        totalBooks = books.size,
        isLoading = false,
        viewMode = mode,
        debugToolsAvailable = true,
    )

    @Test
    fun `shelf in the light theme`() =
        render("library-shelf-light", dark = false, state = state(LibraryViewMode.SHELF))

    @Test
    fun `shelf in the dark theme`() =
        render("library-shelf-dark", dark = true, state = state(LibraryViewMode.SHELF))

    @Test
    fun `list in the light theme`() =
        render("library-list-light", dark = false, state = state(LibraryViewMode.LIST))

    @Test
    fun `list in the dark theme`() =
        render("library-list-dark", dark = true, state = state(LibraryViewMode.LIST))

    @Test
    fun `empty library`() = render(
        "library-empty-light",
        dark = false,
        state = LibraryUiState(isLoading = false, totalBooks = 0),
    )

    @Test
    fun `no matches`() = render(
        "library-no-matches-dark",
        dark = true,
        state = LibraryUiState(
            isLoading = false,
            totalBooks = 6,
            query = "zzz",
            status = BookStatus.READ,
        ),
    )

    private companion object {
        const val SAMPLE_STEP = 7
        const val MIN_COLORS = 25
    }
}
