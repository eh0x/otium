package app.otium.ui.loans

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.otium.domain.error.AppError
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.OpenLoan
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
class LoansContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun open(
        id: Long,
        bookId: Long,
        title: String,
        name: String,
        direction: LoanDirection = LoanDirection.LENT,
        remindOn: Long? = null,
    ) = OpenLoan(
        loan = Loan(id, bookId, name, direction, lentOn = 20_000L, remindOn = remindOn),
        title = title,
        authors = "Author",
        coverPath = null,
        coverSourceUrl = null,
    )

    private fun show(state: LoansUiState, actions: LoansActions = LoansActions()) =
        rule.setContent { OtiumTheme(darkTheme = false) { LoansContent(state, actions) } }

    @Test
    fun `nothing out says so`() {
        show(LoansUiState(isLoading = false))

        rule.onNodeWithText("Lent out").assertIsDisplayed()
        rule.onNodeWithText("Nothing is out.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `while loading it does not claim that nothing is out`() {
        show(LoansUiState(isLoading = true))

        rule.onAllNodesWithText("Nothing is out.", substring = true).assertCountEquals(0)
    }

    @Test
    fun `books lent and books borrowed stand in their own groups`() {
        show(
            LoansUiState(
                isLoading = false,
                loans = listOf(
                    open(1, 10, "The Name of the Wind", "Sam"),
                    open(2, 11, "Dune", "Ana", LoanDirection.BORROWED),
                ),
            ),
        )

        rule.onNodeWithText("LENT TO OTHERS").assertIsDisplayed()
        rule.onNodeWithText("The Name of the Wind").assertIsDisplayed()
        rule.onNodeWithText("Lent to Sam", substring = true).assertIsDisplayed()
        rule.onNodeWithText("BORROWED").assertIsDisplayed()
        rule.onNodeWithText("Borrowed from Ana", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a group with no loans is not shown`() {
        show(
            LoansUiState(isLoading = false, loans = listOf(open(1, 10, "Dune", "Sam"))),
        )

        rule.onAllNodesWithText("BORROWED").assertCountEquals(0)
    }

    @Test
    fun `a reminder day is shown whether or not notifications are allowed`() {
        show(
            LoansUiState(
                isLoading = false,
                loans = listOf(open(1, 10, "Dune", "Sam", remindOn = 20_014L)),
            ),
        )

        rule.onNodeWithText("Reminder on", substring = true).assertIsDisplayed()
    }

    @Test
    fun `returning a book from the list hands back the loan`() {
        var returned: Long? = null
        show(
            LoansUiState(isLoading = false, loans = listOf(open(7, 10, "Dune", "Sam"))),
            LoansActions(onReturned = { returned = it }),
        )

        rule.onNodeWithContentDescription("Mark “Dune” returned").performClick()

        assertEquals(7L, returned)
    }

    @Test
    fun `tapping a book opens it`() {
        var opened: Long? = null
        show(
            LoansUiState(isLoading = false, loans = listOf(open(7, 10, "Dune", "Sam"))),
            LoansActions(onOpenBook = { opened = it }),
        )

        rule.onNodeWithText("Dune").performClick()

        assertEquals(10L, opened)
    }

    @Test
    fun `a failure is told in words`() {
        show(LoansUiState(isLoading = false, error = AppError.NotFound))

        rule.onNodeWithText("We could not find that.").assertIsDisplayed()
    }
}
