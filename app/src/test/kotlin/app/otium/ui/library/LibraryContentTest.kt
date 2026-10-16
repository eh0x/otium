package app.otium.ui.library

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortOrder
import app.otium.domain.usecase.DeriveStatus
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp")
class LibraryContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun summary(
        id: Long,
        title: String,
        authors: String = "Some Author",
        current: Int = 0,
        total: Int = 300,
        shelf: String? = null,
    ) = BookSummary(
        id = id,
        title = title,
        authors = authors,
        coverPath = null,
        coverSourceUrl = null,
        currentPage = current,
        totalPages = total,
        status = DeriveStatus(current, total),
        shelf = shelf,
    )

    private val dune = summary(1, "Dune", "Frank Herbert", current = 120, shelf = "Fiction")
    private val emma = summary(2, "Emma", "Jane Austen", current = 300)

    private fun stateOf(
        books: List<BookSummary> = listOf(dune, emma),
        total: Int = books.size,
        loading: Boolean = false,
        mode: LibraryViewMode = LibraryViewMode.SHELF,
        configure: LibraryUiState.() -> LibraryUiState = { this },
    ) = LibraryUiState(
        books = books,
        totalBooks = total,
        isLoading = loading,
        viewMode = mode,
    ).configure()

    private fun show(state: LibraryUiState, actions: LibraryActions = LibraryActions()) =
        rule.setContent { OtiumTheme(darkTheme = false) { LibraryContent(state, actions) } }

    // --- The four faces ---

    @Test
    fun `an empty library invites the first book`() {
        var opened = 0
        show(stateOf(books = emptyList()), LibraryActions(onOpenAddSheet = { opened++ }))

        rule.onNodeWithText("Your shelves are empty").assertIsDisplayed()
        rule.onNodeWithText("Add a book").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `no matches offers to clear the filters`() {
        var cleared = 0
        show(
            stateOf(books = emptyList(), total = 2) { copy(query = "zzz") },
            LibraryActions(onClearFilters = { cleared++ }),
        )

        rule.onNodeWithText("No volumes match").assertIsDisplayed()
        rule.onNodeWithText("Clear filters").performClick()

        assertEquals(1, cleared)
    }

    // --- Shelves ---

    @Test
    fun `books in progress sit on the desk, then each book on its category shelf`() {
        var opened = -1L
        show(stateOf(), LibraryActions(onOpenBook = { opened = it }))

        rule.onNodeWithText("On the desk").assertIsDisplayed()
        rule.onNodeWithText("1 open").assertIsDisplayed()
        rule.onNodeWithText("Fiction").assertIsDisplayed()
        // Dune is on the desk and on its shelf.
        rule.onAllNodesWithContentDescription("Dune by Frank Herbert").assertCountEquals(2)
        rule.onAllNodesWithContentDescription("Dune by Frank Herbert").onFirst().performClick()
        // The shelves scroll vertically; each shelf's row scrolls sideways.
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Unshelved"))
        rule.onNodeWithText("Unshelved").assertIsDisplayed()

        assertEquals(1L, opened)
    }

    @Test
    fun `the desk is hidden while searching`() {
        show(stateOf { copy(query = "d") })

        rule.onNodeWithText("On the desk").assertDoesNotExist()
        rule.onNodeWithText("Fiction").assertIsDisplayed()
    }

    @Test
    fun `the desk is hidden while a status is filtered`() {
        show(stateOf(books = listOf(dune)) { copy(status = BookStatus.IN_PROGRESS) })

        rule.onNodeWithText("On the desk").assertDoesNotExist()
    }

    // --- Index ---

    @Test
    fun `the index files books under letters and shows status, page and place`() {
        var opened = -1L
        show(stateOf(mode = LibraryViewMode.LIST), LibraryActions(onOpenBook = { opened = it }))

        rule.onNodeWithText("D").assertIsDisplayed()
        rule.onNodeWithText("E").assertIsDisplayed()
        rule.onNodeWithText("Frank Herbert", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Reading · p. 120/300", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("001", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("002", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Emma by Jane Austen").performClick()

        assertEquals(2L, opened)
    }

    @Test
    fun `the index has no letters when sorted by anything but title`() {
        show(stateOf(mode = LibraryViewMode.LIST) { copy(sort = SortOrder.AUTHOR) })

        rule.onNodeWithText("D").assertDoesNotExist()
    }

    @Test
    fun `a book is announced with its status and page, not by color or rule alone`() {
        val unknownTotal = summary(3, "Ulysses", "James Joyce", current = 0, total = 0)
        show(stateOf(books = listOf(dune, unknownTotal), mode = LibraryViewMode.LIST))

        rule.onNodeWithContentDescription("Dune by Frank Herbert").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Reading, page 120 of 300",
            ),
        )
        rule.onNodeWithContentDescription("Ulysses by James Joyce").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unread"),
        )
    }

    // --- Meta line ---

    @Test
    fun `the count follows the visible books`() {
        show(stateOf(books = listOf(dune), mode = LibraryViewMode.LIST))

        rule.onNodeWithText("1 volume").assertIsDisplayed()
    }

    @Test
    fun `the count names the status being shown`() {
        show(
            stateOf(books = listOf(dune), mode = LibraryViewMode.LIST) {
                copy(status = BookStatus.IN_PROGRESS)
            },
        )

        rule.onNodeWithText("1 volume · Reading").assertIsDisplayed()
    }

    @Test
    fun `the sort label opens the arrange sheet`() {
        show(stateOf())

        rule.onNodeWithText("By title").performClick()

        rule.onNodeWithText("Arrange").assertIsDisplayed()
    }

    // --- Search ---

    @Test
    fun `typing in the search field reports the text`() {
        var typed = ""
        show(stateOf(), LibraryActions(onQueryChange = { typed = it }))

        rule.onNode(hasSetTextAction()).performTextInput("dun")

        assertEquals("dun", typed)
    }

    @Test
    fun `the clear button appears only with text and empties the search`() {
        var typed = "unchanged"
        show(stateOf { copy(query = "dun") }, LibraryActions(onQueryChange = { typed = it }))

        rule.onNodeWithContentDescription("Clear search").performClick()

        assertEquals("", typed)
    }

    // --- Status tabs, arrange and layout ---

    @Test
    fun `status tabs select a status and all clears it`() {
        val picked = mutableListOf<BookStatus?>()
        show(stateOf(), LibraryActions(onStatusSelected = { picked += it }))

        rule.onNodeWithText("Reading").performClick()
        rule.onNodeWithText("All").performClick()

        assertEquals(listOf(BookStatus.IN_PROGRESS, null), picked)
    }

    @Test
    fun `the selected status tab is marked selected`() {
        show(stateOf { copy(status = BookStatus.READ) })

        rule.onNode(hasText("Read") and isSelectable()).assertIsSelected()
    }

    @Test
    fun `the arrange sheet offers four orders and reports the choice`() {
        var sort: SortOrder? = null
        show(stateOf(), LibraryActions(onSortSelected = { sort = it }))

        rule.onNodeWithContentDescription("Filter and sort").performClick()
        for (label in listOf("Title", "Author", "Recently updated", "Progress")) {
            rule.onNodeWithText(label).assertIsDisplayed()
        }
        rule.onNodeWithText("Recently added").assertDoesNotExist()
        rule.onNodeWithText("Author").performClick()

        assertEquals(SortOrder.AUTHOR, sort)
    }

    @Test
    fun `the arrange sheet marks the current sort`() {
        show(stateOf { copy(sort = SortOrder.PROGRESS) })

        rule.onNodeWithContentDescription("Filter and sort").performClick()

        rule.onNodeWithText("Progress").assertIsSelected()
    }

    @Test
    fun `the layout button switches from shelves to the index`() {
        var mode: LibraryViewMode? = null
        show(stateOf(), LibraryActions(onViewModeSelected = { mode = it }))

        rule.onNodeWithContentDescription("Change layout").performClick()

        assertEquals(LibraryViewMode.LIST, mode)
    }

    @Test
    fun `the layout button switches from the index to shelves`() {
        var mode: LibraryViewMode? = null
        show(
            stateOf(mode = LibraryViewMode.LIST),
            LibraryActions(onViewModeSelected = { mode = it }),
        )

        rule.onNodeWithContentDescription("Change layout").performClick()

        assertEquals(LibraryViewMode.SHELF, mode)
    }

    // --- Touch targets ---

    @Test
    fun `header controls and status tabs are at least 44 dp`() {
        show(stateOf())

        listOf("Change layout", "Filter and sort").forEach {
            rule.onNodeWithContentDescription(
                it,
            ).assertHeightIsAtLeast(44.dp).assertWidthIsAtLeast(44.dp)
        }
        listOf("All", "Unread", "Reading", "Read").forEach {
            rule.onNode(hasText(it) and isSelectable()).assertHeightIsAtLeast(44.dp)
        }
    }

    // --- Developer tools ---

    @Test
    fun `books that are out add a quiet link to the lent out list`() {
        var opened = 0
        show(
            stateOf { copy(openLoans = 2) },
            LibraryActions(onOpenLoans = { opened++ }),
        )

        rule.onNodeWithText("Lent out · 2").assertIsDisplayed().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `with nothing out there is no lent out link`() {
        show(stateOf())

        rule.onNodeWithText("Lent out", substring = true).assertDoesNotExist()
    }

    @Test
    fun `developer tools appear only when available`() {
        var seeded = 0
        var catalog = 0
        show(
            stateOf { copy(debugToolsAvailable = true) },
            LibraryActions(onSeedDebugData = { seeded++ }, onOpenCatalog = { catalog++ }),
        )

        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Seed 5,000 test books").performClick()
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Theme catalog").performClick()

        assertEquals(1, seeded)
        assertEquals(1, catalog)
    }

    @Test
    fun `without developer tools there is no menu`() {
        show(stateOf())

        rule.onNodeWithContentDescription("More options").assertDoesNotExist()
    }
}
