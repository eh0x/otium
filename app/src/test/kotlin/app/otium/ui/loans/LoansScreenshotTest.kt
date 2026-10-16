package app.otium.ui.loans

import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.otium.domain.model.Loan
import app.otium.domain.model.LoanDirection
import app.otium.domain.model.OpenLoan
import app.otium.ui.saveScreenshot
import app.otium.ui.theme.OtiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the Lent out screen headlessly to build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h740dp-xxhdpi")
class LoansScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private fun open(
        id: Long,
        title: String,
        name: String,
        direction: LoanDirection,
        remindOn: Long? = null,
    ) = OpenLoan(
        loan = Loan(id, id, name, direction, lentOn = 20_000L, remindOn = remindOn),
        title = title,
        authors = "Author",
        coverPath = null,
        coverSourceUrl = null,
    )

    private val state = LoansUiState(
        isLoading = false,
        loans = listOf(
            open(1, "The Name of the Wind", "Sam", LoanDirection.LENT, remindOn = 20_030L),
            open(2, "Meditations", "Ana", LoanDirection.LENT),
            open(3, "Dune", "Mira", LoanDirection.BORROWED),
        ),
    )

    private fun render(name: String, dark: Boolean, state: LoansUiState = this.state) {
        rule.setContent {
            OtiumTheme(darkTheme = dark) { LoansContent(state, LoansActions()) }
        }
        rule.saveScreenshot(name)
    }

    @Test
    fun `lent out in the light theme`() = render("loans-light", dark = false)

    @Test
    fun `lent out in the dark theme`() = render("loans-dark", dark = true)

    @Test
    fun `lent out with nothing out`() =
        render("loans-empty", dark = false, state = LoansUiState(isLoading = false))
}
