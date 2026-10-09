package app.otium.ui.library

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.otium.domain.model.BookStatus
import app.otium.domain.model.BookSummary
import app.otium.domain.model.LibraryViewMode
import app.otium.domain.model.SortOrder
import app.otium.domain.usecase.DeriveStatus
import app.otium.ui.add.AddMethod
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
    ) = BookSummary(
        id = id,
        title = title,
        authors = authors,
        coverPath = null,
        coverSourceUrl = null,
        currentPage = current,
        totalPages = total,
        status = DeriveStatus(current, total),
    )

    private val dune = summary(1, "Dune", "Frank Herbert", current = 120)
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
        val added = mutableListOf<AddMethod>()
        show(stateOf(books = emptyList()), LibraryActions(onAddBook = { added += it }))

        rule.onNodeWithText("Your shelves are empty").assertIsDisplayed()
        rule.onNodeWithText("Add a book").performClick()
        rule.onNodeWithText("Scan barcode").performClick()

        assertEquals(listOf(AddMethod.SCAN), added)
    }

    @Test
    fun `no matches offers to clear the filters`() {
        var cleared = 0
        show(
            stateOf(books = emptyList(), total = 2) { copy(query = "zzz") },
            LibraryActions(onClearFilters = { cleared++ }),
        )

        rule.onNodeWithText("No books match").assertIsDisplayed()
        rule.onNodeWithText("Clear filters").performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun `shelf view shows each book and opens it on tap`() {
        var opened = -1L
        show(stateOf(), LibraryActions(onOpenBook = { opened = it }))

        // Each book is one grouped item for screen readers; its parts live in the unmerged tree.
        // Without a cover image the title shows twice: on the generated cover and as the caption.
        rule.onAllNodesWithText("Dune", useUnmergedTree = true).assertCountEquals(2)
        rule.onAllNodesWithText("Emma", useUnmergedTree = true).assertCountEquals(2)
        rule.onNodeWithContentDescription("Dune by Frank Herbert").performClick()

        assertEquals(1L, opened)
    }

    @Test
    fun `list view shows author and status and opens the book on tap`() {
        var opened = -1L
        show(stateOf(mode = LibraryViewMode.LIST), LibraryActions(onOpenBook = { opened = it }))

        rule.onNodeWithText("Frank Herbert", useUnmergedTree = true).assertIsDisplayed()
        // Each status shows twice: on the filter chip and on the book's row.
        rule.onAllNodesWithText("In progress", useUnmergedTree = true).assertCountEquals(2)
        rule.onAllNodesWithText("Read", useUnmergedTree = true).assertCountEquals(2)
        rule.onNodeWithContentDescription("Emma by Jane Austen").performClick()

        assertEquals(2L, opened)
    }

    @Test
    fun `a book is announced with its status and page, not by color or bar alone`() {
        val unknownTotal = summary(3, "Ulysses", "James Joyce", current = 0, total = 0)
        show(stateOf(books = listOf(dune, unknownTotal)))

        rule.onNodeWithContentDescription("Dune by Frank Herbert").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "In progress, page 120 of 300",
            ),
        )
        rule.onNodeWithContentDescription("Ulysses by James Joyce").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not started"),
        )
    }

    @Test
    fun `the book count follows the visible books`() {
        show(stateOf(books = listOf(dune)))

        rule.onNodeWithText("1 book").assertIsDisplayed()
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

    // --- Filters and sorting ---

    @Test
    fun `status chips select a status and all clears it`() {
        val picked = mutableListOf<BookStatus?>()
        show(stateOf(), LibraryActions(onStatusSelected = { picked += it }))

        rule.onNodeWithText("In progress").performClick()
        rule.onNodeWithText("All").performClick()

        assertEquals(listOf(BookStatus.IN_PROGRESS, null), picked)
    }

    @Test
    fun `the selected status chip is marked selected`() {
        show(stateOf { copy(status = BookStatus.READ) })

        rule.onNodeWithText("Read").assertIsSelected()
    }

    @Test
    fun `the sort menu offers every order and reports the choice`() {
        var sort: SortOrder? = null
        show(stateOf(), LibraryActions(onSortSelected = { sort = it }))

        rule.onNodeWithText("Sort").performClick()
        for (label in listOf("Title", "Author", "Recently added", "Recently updated", "Progress")) {
            rule.onNodeWithText(label).assertIsDisplayed()
        }
        rule.onNodeWithText("Author").performClick()

        assertEquals(SortOrder.AUTHOR, sort)
    }

    @Test
    fun `the view button offers the list layout from the shelf`() {
        var mode: LibraryViewMode? = null
        show(stateOf(), LibraryActions(onViewModeSelected = { mode = it }))

        rule.onNodeWithText("List").performClick()

        assertEquals(LibraryViewMode.LIST, mode)
    }

    @Test
    fun `the view button offers the shelf layout from the list`() {
        var mode: LibraryViewMode? = null
        show(
            stateOf(mode = LibraryViewMode.LIST),
            LibraryActions(onViewModeSelected = { mode = it }),
        )

        rule.onNodeWithText("Shelf").performClick()

        assertEquals(LibraryViewMode.SHELF, mode)
    }

    @Test
    fun `the filter sheet lists categories and tags and toggles them`() {
        val categories = mutableListOf<String>()
        val tags = mutableListOf<String>()
        show(
            stateOf {
                copy(availableCategories = listOf("Fiction"), availableTags = listOf("favourite"))
            },
            LibraryActions(onCategoryToggled = { categories += it }, onTagToggled = { tags += it }),
        )

        rule.onNodeWithText("Filters").performClick()
        rule.onNodeWithText("Fiction").performClick()
        rule.onNodeWithText("favourite").performClick()

        assertEquals(listOf("Fiction"), categories)
        assertEquals(listOf("favourite"), tags)
    }

    @Test
    fun `the filters button counts the selected categories and tags`() {
        show(stateOf { copy(categories = setOf("Fiction"), tags = setOf("lent", "favourite")) })

        rule.onNodeWithText("Filters (3)").assertIsDisplayed()
    }

    @Test
    fun `the filter sheet explains itself when there are no labels yet`() {
        show(stateOf())

        rule.onNodeWithText("Filters").performClick()

        rule.onNodeWithText("Categories and tags appear here once your books have them.")
            .assertIsDisplayed()
    }

    // --- Adding and developer tools ---

    @Test
    fun `the add button offers the ways to add and reports the choice`() {
        val added = mutableListOf<AddMethod>()
        show(stateOf(), LibraryActions(onAddBook = { added += it }))

        rule.onNodeWithContentDescription("Add book").performClick()
        rule.onNodeWithText("Search by title").performClick()

        assertEquals(listOf(AddMethod.TITLE_SEARCH), added)
        rule.onNodeWithText("Search by title").assertDoesNotExist()
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
    fun `the menu opens backup and restore`() {
        var opened = 0
        show(stateOf(), LibraryActions(onOpenBackup = { opened++ }))

        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Backup and restore").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `without developer tools the menu holds no developer items`() {
        show(stateOf())

        rule.onNodeWithContentDescription("More options").performClick()

        rule.onNodeWithText("Backup and restore").assertIsDisplayed()
        rule.onAllNodesWithText("Seed 5,000 test books").assertCountEquals(0)
        rule.onAllNodesWithText("Theme catalog").assertCountEquals(0)
    }
}
