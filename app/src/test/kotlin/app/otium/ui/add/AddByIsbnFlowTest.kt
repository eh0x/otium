package app.otium.ui.add

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.otium.MainActivity
import app.otium.di.RepositoryModule
import app.otium.di.WorkModule
import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import app.otium.domain.fakes.FakeBookRepository
import app.otium.domain.fakes.FakeCoverStore
import app.otium.domain.fakes.FakeMetadataRepository
import app.otium.domain.fakes.FixedClock
import app.otium.domain.fakes.book
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.domain.port.Clock
import app.otium.domain.port.CoverStore
import app.otium.domain.port.LookupScheduler
import app.otium.domain.repo.BookRepository
import app.otium.domain.repo.MetadataRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The manual ISBN path end to end: the real activity, navigation graph and ViewModels, with the
 * library, metadata services and cover store replaced by fakes.
 */
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, WorkModule::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h740dp")
class AddByIsbnFlowTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val library = FakeBookRepository()
    private val services = FakeMetadataRepository().apply {
        isbnResult = Try.Success(
            BookCandidate(
                isbn13 = DUNE,
                title = "Dune",
                authors = listOf("Frank Herbert"),
                totalPages = 412,
                source = BookSource.GOOGLE,
            ),
        )
    }

    @BindValue @JvmField
    val books: BookRepository = library

    @BindValue @JvmField
    val metadata: MetadataRepository = services

    @BindValue @JvmField
    val covers: CoverStore = FakeCoverStore()

    @BindValue @JvmField
    val clock: Clock = FixedClock()

    private var lookupsPlanned = 0

    @BindValue @JvmField
    val scheduler: LookupScheduler = LookupScheduler { lookupsPlanned++ }

    private fun waitFor(text: String) = rule.waitUntil(TIMEOUT) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openIsbnEntry() {
        rule.onNodeWithContentDescription("Add book").performClick()
        rule.onNodeWithText("Enter ISBN").performClick()
        waitFor("Look up")
    }

    private fun lookUp(isbn: String) {
        rule.onNodeWithText("ISBN").performTextInput(isbn)
        rule.onNodeWithText("Look up").performClick()
    }

    private fun pressBack() =
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `a typed isbn is looked up, confirmed and saved`() {
        openIsbnEntry()
        lookUp(DUNE)

        waitFor("Check the details")
        rule.onNodeWithText("Dune").assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()

        rule.waitUntil(TIMEOUT) { library.all.isNotEmpty() }
        val saved = library.all.single()
        assertEquals("Dune", saved.title)
        assertEquals(412, saved.totalPages)
        waitFor("Frank Herbert")

        // The add flow is gone from the back stack: back returns to the library.
        pressBack()
        waitFor("Library")
    }

    @Test
    fun `an isbn already in the library opens that book`() {
        library.seed(book(isbn13 = DUNE, title = "My Dune"))
        openIsbnEntry()
        lookUp(DUNE)

        waitFor("My Dune")
        assertTrue("nothing is looked up for an owned book", services.lookedUp.isEmpty())
        assertEquals(1, library.all.size)
    }

    @Test
    fun `offline the isbn is kept for a later lookup`() {
        services.isbnResult = Try.Failure(AppError.Offline)
        openIsbnEntry()
        lookUp(DUNE)

        waitFor("Save the ISBN, fill in details later")
        rule.onNodeWithText("Save the ISBN, fill in details later").performClick()

        rule.waitUntil(TIMEOUT) { library.all.isNotEmpty() }
        assertTrue(library.all.single().needsLookup)
        assertEquals(1, lookupsPlanned)
    }

    private companion object {
        const val DUNE = "9780441013593"
        const val TIMEOUT = 5_000L
    }
}
