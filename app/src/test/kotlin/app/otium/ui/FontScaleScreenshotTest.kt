package app.otium.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.otium.domain.fakes.book
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.DriveAuthState
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortKeys
import app.otium.domain.usecase.DeriveStatus
import app.otium.ui.backup.BackupActions
import app.otium.ui.backup.BackupContent
import app.otium.ui.backup.BackupUiState
import app.otium.ui.detail.DetailActions
import app.otium.ui.detail.DetailContent
import app.otium.ui.detail.DetailUiState
import app.otium.ui.library.LibraryActions
import app.otium.ui.library.LibraryContent
import app.otium.ui.library.LibraryUiState
import app.otium.ui.navigation.OtiumBottomBar
import app.otium.ui.navigation.TopLevelDestination
import app.otium.ui.reading.ReadingActions
import app.otium.ui.reading.ReadingContent
import app.otium.ui.reading.ReadingUiState
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screens at 200% font scale in the dark theme, written to build/screenshots so
 * clipping and overlap at large text sizes are seen rather than guessed at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi", fontScale = 2.0f)
class FontScaleScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val books = listOf(
        Triple("Meditations", "Marcus Aurelius", 212),
        Triple("The Name of the Rose", "Umberto Eco", 0),
        Triple("Ethics", "Baruch Spinoza", 300),
    ).mapIndexed { index, (title, author, current) ->
        BookSummary(
            id = index + 1L,
            title = title,
            authors = author,
            coverPath = null,
            coverSourceUrl = null,
            currentPage = current,
            totalPages = 300,
            status = DeriveStatus(current, 300),
            shelf = "Philosophy",
            updatedAt = index.toLong(),
        )
    }.sortedWith(SortKeys.byTitle)

    private fun render(name: String, content: @Composable () -> Unit) {
        rule.setContent { OtiumTheme(darkTheme = true) { content() } }
        rule.saveScreenshot(name)
    }

    private fun library(mode: LibraryViewMode) = LibraryUiState(
        books = books,
        totalBooks = books.size,
        isLoading = false,
        viewMode = mode,
    )

    @Test
    fun `library shelves at 200 percent`() = render("scale200-library-shelves") {
        LibraryContent(library(LibraryViewMode.SHELF), LibraryActions())
    }

    @Test
    fun `library index at 200 percent`() = render("scale200-library-index") {
        LibraryContent(library(LibraryViewMode.LIST), LibraryActions())
    }

    @Test
    fun `detail at 200 percent`() = render("scale200-detail") {
        val meditations = book(title = "Meditations", totalPages = 304, currentPage = 212)
            .copy(authors = listOf("Marcus Aurelius"), categories = listOf("Philosophy"))
        DetailContent(DetailUiState(isLoading = false, book = meditations), DetailActions())
    }

    @Test
    fun `reading at 200 percent`() = render("scale200-reading") {
        ReadingContent(
            ReadingUiState(
                isLoading = false,
                open = books.filter { it.status == BookStatus.IN_PROGRESS },
                finished = books.filter { it.status == BookStatus.READ },
            ),
            ReadingActions(),
        )
    }

    @Test
    fun `settings at 200 percent`() = render("scale200-settings") {
        BackupContent(
            BackupUiState(auth = DriveAuthState.SignedIn("reader@example.org")),
            BackupActions(),
        )
    }

    @Test
    fun `bottom bar at 200 percent`() = render("scale200-bottom-bar") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            OtiumBottomBar(selected = TopLevelDestination.READING, onSelect = {}, onAdd = {})
        }
    }
}
