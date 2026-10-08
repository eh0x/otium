package app.otium.data.remote.googlebooks

import app.otium.data.remote.RemoteJson
import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import app.otium.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleBooksMapperTest {

    private fun parse(fixture: String): VolumesResponse =
        RemoteJson.decodeFromString(Fixtures.read("googlebooks/$fixture"))

    private fun first(fixture: String): BookCandidate =
        checkNotNull(parse(fixture).toCandidates().firstOrNull()) { "No candidate in $fixture" }

    @Test
    fun `a full record maps every field`() {
        val candidate = first("volumes-full.json")

        assertEquals("The Google Story (2018 Updated Edition)", candidate.title)
        assertEquals(
            "Inside the Hottest Business, Media, and Technology Success of Our Time",
            candidate.subtitle,
        )
        assertEquals(listOf("David A. Vise", "Mark Malseed"), candidate.authors)
        assertEquals("Random House Publishing Group", candidate.publisher)
        assertEquals(2005, candidate.publishedYear)
        assertEquals("en", candidate.language)
        assertEquals(384, candidate.totalPages)
        assertEquals("9780440335702", candidate.isbn13)
        assertEquals("0440335701", candidate.isbn10)
        assertEquals(BookSource.GOOGLE, candidate.source)
    }

    @Test
    fun `html in the description is removed`() {
        val description = checkNotNull(first("volumes-full.json").description)

        assertTrue(description.startsWith("The definitive, bestselling account"))
        assertFalse(description.contains("<"))
        assertFalse(description.contains(">"))
    }

    @Test
    fun `category paths are split, deduplicated and capped`() {
        assertEquals(
            listOf("Fiction", "Science Fiction", "Space Opera", "Classics", "Fantasy"),
            first("volumes-dune.json").categories,
        )
        assertEquals(
            listOf(
                "Business & Economics",
                "Entrepreneurship",
                "Computers",
                "Information Technology",
                "History",
            ),
            first("volumes-full.json").categories,
        )
    }

    @Test
    fun `the largest cover is used over https without the page curl`() {
        val cover = checkNotNull(first("volumes-full.json").coverUrl)

        assertTrue(cover.startsWith("https://books.google.com/books/content?id=zyTCAlFPjgYC"))
        assertTrue(cover.contains("zoom=4"))
        assertFalse(cover.contains("edge=curl"))
    }

    @Test
    fun `a thumbnail is used when it is the only size`() {
        val cover = checkNotNull(first("volumes-dune.json").coverUrl)

        assertTrue(cover.startsWith("https://"))
        assertTrue(cover.contains("zoom=1"))
    }

    @Test
    fun `a missing page count is unknown, not zero`() {
        assertNull(first("volumes-no-pages.json").totalPages)
    }

    @Test
    fun `a non-https cover url is rejected`() {
        val response = RemoteJson.decodeFromString<VolumesResponse>(
            """{"items":[{"volumeInfo":{"title":"A","imageLinks":{"thumbnail":"file:///etc/passwd"}}}]}""",
        )
        assertNull(response.toCandidates().first().coverUrl)
    }

    @Test
    fun `a missing cover is null`() {
        assertNull(first("volumes-no-cover.json").coverUrl)
    }

    @Test
    fun `missing authors map to an empty list`() {
        assertEquals(emptyList<String>(), first("volumes-no-authors.json").authors)
    }

    @Test
    fun `fields of the wrong type are ignored instead of failing the whole record`() {
        val candidate = first("volumes-malformed.json")

        assertEquals("The Google Story (2018 Updated Edition)", candidate.title)
        assertNull(candidate.totalPages)
        assertEquals(emptyList<String>(), candidate.authors)
        assertEquals(emptyList<String>(), candidate.categories)
        assertEquals(2018, candidate.publishedYear)
    }

    @Test
    fun `an empty result has no candidates`() {
        assertEquals(emptyList<BookCandidate>(), parse("volumes-empty.json").toCandidates())
    }

    @Test
    fun `a search maps every volume`() {
        val titles = parse("volumes-search.json").toCandidates().map { it.title }

        assertEquals(listOf("Dune", "The Google Story (2018 Updated Edition)"), titles)
    }

    @Test
    fun `a volume without a title is dropped`() {
        val response = RemoteJson.decodeFromString<VolumesResponse>(
            """{"items":[{"volumeInfo":{"authors":["Nobody"]}},{"volumeInfo":{"title":"Kept"}}]}""",
        )

        assertEquals(listOf("Kept"), response.toCandidates().map { it.title })
    }

    @Test
    fun `a year-only and a year-month date both give the year`() {
        val response = RemoteJson.decodeFromString<VolumesResponse>(
            """{"items":[
                {"volumeInfo":{"title":"A","publishedDate":"1965"}},
                {"volumeInfo":{"title":"B","publishedDate":"1999-04"}},
                {"volumeInfo":{"title":"C","publishedDate":"unknown"}}
            ]}""",
        )

        assertEquals(listOf(1965, 1999, null), response.toCandidates().map { it.publishedYear })
    }
}
