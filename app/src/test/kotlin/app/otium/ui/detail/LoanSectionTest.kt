package app.otium.ui.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.otium.domain.fakes.book
import app.otium.domain.model.Book
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.ui.theme.OtiumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp")
class LoanSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val dune: Book = book(title = "Dune", totalPages = 400, currentPage = 100)

    private fun loan(
        id: Long,
        name: String,
        direction: LoanDirection = LoanDirection.LENT,
        lentOn: Long = 20_000L,
        remindOn: Long? = null,
        returnedOn: Long? = null,
    ) = Loan(id, 1L, name, direction, lentOn, remindOn, returnedOn)

    private fun show(loans: List<Loan>, actions: DetailActions = DetailActions()) =
        rule.setContent {
            OtiumTheme(darkTheme = false) {
                DetailContent(DetailUiState(isLoading = false, book = dune, loans = loans), actions)
            }
        }

    private fun typeInto(label: String, text: String) {
        rule.onNode(
            hasContentDescription(label) and hasSetTextAction(),
        ).performTextReplacement(text)
    }

    private fun click(text: String) {
        rule.onNodeWithText(text).performScrollTo().performClick()
    }

    // --- Reading ---

    @Test
    fun `a book that is not out offers to lend it or note it as borrowed`() {
        show(emptyList())

        rule.onNodeWithText("LENDING").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Not lent out and not borrowed.").assertIsDisplayed()
        rule.onNodeWithText("Lend").assertIsDisplayed()
        rule.onNodeWithText("Borrowed").assertIsDisplayed()
    }

    @Test
    fun `a book that is out says who has it and offers to mark it returned`() {
        show(listOf(loan(1, "Sam")))

        rule.onNodeWithText("Lent to Sam").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Since", substring = true).assertIsDisplayed()
        rule.onNodeWithText("No reminder").assertIsDisplayed()
        rule.onNodeWithText("Returned").assertIsDisplayed()
        rule.onAllNodesWithText("Lend").assertCountEquals(0)
    }

    @Test
    fun `a borrowed book says who it is from`() {
        show(listOf(loan(1, "Ana", LoanDirection.BORROWED)))

        rule.onNodeWithText("Borrowed from Ana").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a reminder day is shown`() {
        show(listOf(loan(1, "Sam", remindOn = 20_014L)))

        rule.onNodeWithText("Reminder on", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `returned loans stay as one line of history`() {
        show(listOf(loan(2, "Sam"), loan(1, "Ana", lentOn = 19_000L, returnedOn = 19_010L)))

        rule.onNodeWithText("Lent to Ana, ", substring = true).performScrollTo().assertIsDisplayed()
    }

    // --- Lending ---

    @Test
    fun `lending asks for a name, and saving hands the draft back`() {
        var draft: LoanDraft? = null
        show(emptyList(), DetailActions(onLoanOpened = { draft = it }))

        click("Lend")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        typeInto("Lent to", "  Sam ")
        rule.onNodeWithText("Save").assertIsEnabled().performClick()

        assertEquals(LoanDraft(LoanDirection.LENT, "Sam", daysAgo = 0, remindInDays = null), draft)
    }

    @Test
    fun `the day and the reminder can be chosen, and a reminder explains itself`() {
        var draft: LoanDraft? = null
        show(emptyList(), DetailActions(onLoanOpened = { draft = it }))

        click("Lend")
        rule.onAllNodesWithText("One quiet notification", substring = true).assertCountEquals(0)
        typeInto("Lent to", "Sam")
        rule.onNodeWithText("A week ago").performClick()
        rule.onNodeWithText("In two weeks").performClick()
        rule.onNodeWithText("It comes as one quiet notification", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()

        assertEquals(LoanDraft(LoanDirection.LENT, "Sam", daysAgo = 7, remindInDays = 14), draft)
    }

    @Test
    fun `noting a borrowed book asks who it is from`() {
        var draft: LoanDraft? = null
        show(emptyList(), DetailActions(onLoanOpened = { draft = it }))

        click("Borrowed")
        typeInto("Borrowed from", "Ana")
        rule.onNodeWithText("Save").performClick()

        assertEquals(LoanDirection.BORROWED, draft?.direction)
        assertEquals("Ana", draft?.name)
    }

    @Test
    fun `cancelling the form lends nothing`() {
        var draft: LoanDraft? = null
        show(emptyList(), DetailActions(onLoanOpened = { draft = it }))

        click("Lend")
        typeInto("Lent to", "Sam")
        rule.onNodeWithText("Cancel").performClick()

        assertNull(draft)
        rule.onNodeWithText("Not lent out and not borrowed.").assertIsDisplayed()
    }

    // --- Returning, reminding, removing ---

    @Test
    fun `marking a loan returned hands back its id`() {
        var returned: Long? = null
        show(listOf(loan(5, "Sam")), DetailActions(onLoanReturned = { returned = it }))

        click("Returned")

        assertEquals(5L, returned)
    }

    @Test
    fun `the reminder can be changed or cleared`() {
        var changed: Pair<Long, Int?>? = null
        show(
            listOf(loan(5, "Sam", remindOn = 20_007L)),
            DetailActions(onReminderChanged = { id, days -> changed = id to days }),
        )

        click("Reminder")
        rule.onNodeWithText("In a month").performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals(5L to 30, changed)

        click("Reminder")
        rule.onNodeWithText("No").performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals(5L to null, changed)
    }

    @Test
    fun `removing a name from the history asks first and says what stays behind`() {
        var removed: Long? = null
        show(
            listOf(loan(1, "Ana", returnedOn = 20_003L)),
            DetailActions(onLoanRemoved = { removed = it }),
        )

        rule.onNodeWithContentDescription("Remove Ana from the history")
            .performScrollTo().performClick()
        rule.onNodeWithText("Backups and exports you made earlier still hold it.", substring = true)
            .assertIsDisplayed()
        assertNull(removed)
        rule.onNodeWithText("Delete").performClick()

        assertEquals(1L, removed)
    }

    @Test
    fun `cancelling the removal keeps the history`() {
        var removed: Long? = null
        show(
            listOf(loan(1, "Ana", returnedOn = 20_003L)),
            DetailActions(onLoanRemoved = { removed = it }),
        )

        rule.onNodeWithContentDescription("Remove Ana from the history")
            .performScrollTo().performClick()
        rule.onNodeWithText("Cancel").performClick()

        assertNull(removed)
        rule.onNodeWithText("Lent to Ana, ", substring = true).assertIsDisplayed()
    }
}
