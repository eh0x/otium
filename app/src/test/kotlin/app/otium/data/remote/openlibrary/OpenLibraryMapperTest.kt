package app.otium.data.remote.openlibrary

import app.otium.data.remote.RemoteJson
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibraryMapperTest {

    private inline fun <reified T> read(fixture: String): T =
        RemoteJson.decodeFromString(Fixtures.read("openlibrary/$fixture"))

    private val duneEdition: EditionDto = read("edition-dune.json")
    private val duneWork: WorkDto = read("work-dune.json")
    private val duneAuthor: AuthorDto = read("author-dune.json")

    @Test
    fun `an edition with its work and authors maps every field`() {
        val candidate = duneEdition.toCandidate(
            authorNames = listOf(checkNotNull(duneAuthor.name)),
            work = duneWork,
        )

        assertEquals("Dune", candidate.title)
        assertEquals(listOf("Frank Herbert"), candidate.authors)
        assertEquals("Ace Trade", candidate.publisher)
        assertEquals(2005, candidate.publishedYear)
        assertEquals("en", candidate.language)
        assertEquals(544, candidate.totalPages)
        assertEquals("9780441013593", candidate.isbn13)
        assertEquals("0441013597", candidate.isbn10)
        assertEquals(BookSource.OPEN_LIBRARY, candidate.source)
    }

    @Test
    fun `the recorded series entry gives the series and its number`() {
        val candidate = duneEdition.toCandidate(authorNames = emptyList(), work = null)

        assertEquals("Dune", candidate.series)
        assertEquals(1.0, candidate.seriesIndex)
    }

    @Test
    fun `an edition without a series entry has none`() {
        val candidate = read<EditionDto>("edition-hobbit.json")
            .toCandidate(authorNames = emptyList(), work = null)

        assertNull(candidate.series)
        assertNull(candidate.seriesIndex)
    }

    @Test
    fun `the description and subjects come from the work`() {
        val candidate = duneEdition.toCandidate(authorNames = emptyList(), work = duneWork)

        assertTrue(candidate.description.orEmpty().startsWith("Set on the desert planet Arrakis"))
        assertEquals(
            listOf("Dune (Imaginary place)", "Fiction", "Fiction, science fiction, general"),
            candidate.categories,
        )
    }

    @Test
    fun `without a work there is no description and no categories`() {
        val candidate = duneEdition.toCandidate(authorNames = emptyList(), work = null)

        assertNull(candidate.description)
        assertEquals(emptyList<String>(), candidate.categories)
    }

    @Test
    fun `the first real cover id builds a large cover url`() {
        val candidate = duneEdition.toCandidate(emptyList(), null)

        assertEquals("https://covers.openlibrary.org/b/id/14565843-L.jpg", candidate.coverUrl)
    }

    @Test
    fun `a negative cover id is skipped`() {
        val edition: EditionDto = RemoteJson.decodeFromString(
            """{"title":"T","covers":[-1,555],"isbn_13":["9780441013593"]}""",
        )

        assertEquals(
            "https://covers.openlibrary.org/b/id/555-L.jpg",
            edition.toCandidate(emptyList(), null).coverUrl,
        )
    }

    @Test
    fun `with no cover id the isbn cover is used and 404s instead of a blank image`() {
        val edition: EditionDto = RemoteJson.decodeFromString(
            """{"title":"T","isbn_13":["9780441013593"]}""",
        )

        assertEquals(
            "https://covers.openlibrary.org/b/isbn/9780441013593-L.jpg?default=false",
            edition.toCandidate(emptyList(), null).coverUrl,
        )
    }

    @Test
    fun `no cover id and no isbn means no cover`() {
        val edition: EditionDto = RemoteJson.decodeFromString("""{"title":"T"}""")

        assertNull(edition.toCandidate(emptyList(), null).coverUrl)
    }

    @Test
    fun `a record without a page count is unknown and a bare year is read`() {
        val edition: EditionDto = read("edition-google-story.json")

        val candidate = edition.toCandidate(emptyList(), null)

        assertNull(candidate.totalPages)
        assertEquals(2005, candidate.publishedYear)
    }

    @Test
    fun `author and work keys are exposed for the follow-up requests`() {
        assertEquals(listOf("/authors/OL79034A"), duneEdition.authorKeys())
        assertEquals("/works/OL893414W", duneEdition.workKey())
    }

    @Test
    fun `a description given as a typed object is read`() {
        val work: WorkDto = RemoteJson.decodeFromString(
            """{"description":{"type":"/type/text","value":"A plain text."}}""",
        )

        assertEquals("A plain text.", work.descriptionText())
    }

    @Test
    fun `a description given as a string is read`() {
        assertTrue(duneWork.descriptionText().orEmpty().startsWith("Set on the desert planet"))
    }

    @Test
    fun `a search maps each hit`() {
        val results: SearchResponse = read("search-dune.json")

        val candidate = results.toCandidates().first()

        assertEquals("Dune", candidate.title)
        assertEquals(listOf("Frank Herbert"), candidate.authors)
        assertEquals(1965, candidate.publishedYear)
        assertEquals("9780441013593", candidate.isbn13)
        assertEquals(608, candidate.totalPages)
        assertEquals("Dom Wydawniczy REBIS Sp. z o.o.", candidate.publisher)
        assertEquals("fr", candidate.language)
        assertEquals("https://covers.openlibrary.org/b/id/11481354-L.jpg", candidate.coverUrl)
        assertEquals(BookSource.OPEN_LIBRARY, candidate.source)
    }

    @Test
    fun `an empty search has no candidates`() {
        val results: SearchResponse = read("search-empty.json")

        assertEquals(emptyList<BookCandidate>(), results.toCandidates())
    }

    @Test
    fun `fields of the wrong type are ignored instead of failing the record`() {
        val edition: EditionDto = RemoteJson.decodeFromString(
            """{"title":"T","number_of_pages":"lots","publishers":"Someone","covers":"none"}""",
        )

        val candidate = edition.toCandidate(emptyList(), null)

        assertEquals("T", candidate.title)
        assertNull(candidate.totalPages)
        assertNull(candidate.publisher)
    }
}
