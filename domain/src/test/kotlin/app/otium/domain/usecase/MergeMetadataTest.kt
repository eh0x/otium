package app.otium.domain.usecase

import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeMetadataTest {

    private fun google(
        title: String = "Google Title",
        authors: List<String> = listOf("G Author"),
        description: String? = "Google description",
        totalPages: Int? = 320,
        publisher: String? = "Google Press",
        coverUrl: String? = "https://google/cover.jpg",
        categories: List<String> = listOf("Fiction"),
    ) = BookCandidate(
        isbn13 = "9780306406157",
        title = title,
        authors = authors,
        description = description,
        totalPages = totalPages,
        publisher = publisher,
        coverUrl = coverUrl,
        categories = categories,
        source = BookSource.GOOGLE,
    )

    private fun openLibrary(
        title: String = "OL Title",
        authors: List<String> = listOf("OL Author"),
        description: String? = "OL description",
        totalPages: Int? = 300,
        publisher: String? = "OL Press",
        coverUrl: String? = "https://ol/cover.jpg",
        categories: List<String> = listOf("Novel"),
    ) = BookCandidate(
        isbn13 = "9780306406157",
        title = title,
        authors = authors,
        description = description,
        totalPages = totalPages,
        publisher = publisher,
        coverUrl = coverUrl,
        categories = categories,
        source = BookSource.OPEN_LIBRARY,
    )

    @Test
    fun `no sources gives no candidate`() {
        assertNull(MergeMetadata(google = null, openLibrary = null))
    }

    @Test
    fun `only google keeps google data and source`() {
        val merged = MergeMetadata(google = google(), openLibrary = null)!!
        assertEquals("Google Title", merged.title)
        assertEquals(BookSource.GOOGLE, merged.source)
    }

    @Test
    fun `only open library keeps its data and source`() {
        val merged = MergeMetadata(google = null, openLibrary = openLibrary())!!
        assertEquals("OL Title", merged.title)
        assertEquals(BookSource.OPEN_LIBRARY, merged.source)
    }

    @Test
    fun `google wins title authors and description`() {
        val merged = MergeMetadata(google = google(), openLibrary = openLibrary())!!
        assertEquals("Google Title", merged.title)
        assertEquals(listOf("G Author"), merged.authors)
        assertEquals("Google description", merged.description)
        assertEquals(BookSource.GOOGLE, merged.source)
    }

    @Test
    fun `blank google title and empty authors fall back to open library`() {
        val merged = MergeMetadata(
            google = google(title = "  ", authors = emptyList(), description = null),
            openLibrary = openLibrary(),
        )!!
        assertEquals("OL Title", merged.title)
        assertEquals(listOf("OL Author"), merged.authors)
        assertEquals("OL description", merged.description)
    }

    @Test
    fun `gaps in google are filled from open library`() {
        val merged = MergeMetadata(
            google = google(totalPages = null, publisher = null, categories = emptyList()),
            openLibrary = openLibrary(),
        )!!
        assertEquals(300, merged.totalPages)
        assertEquals("OL Press", merged.publisher)
        assertEquals(listOf("Novel"), merged.categories)
    }

    @Test
    fun `zero google pages count as a gap`() {
        val merged = MergeMetadata(google = google(totalPages = 0), openLibrary = openLibrary())!!
        assertEquals(300, merged.totalPages)
    }

    @Test
    fun `google values are kept when present`() {
        val merged = MergeMetadata(google = google(), openLibrary = openLibrary())!!
        assertEquals(320, merged.totalPages)
        assertEquals("Google Press", merged.publisher)
        assertEquals(listOf("Fiction"), merged.categories)
    }

    @Test
    fun `google cover is preferred when usable`() {
        val merged = MergeMetadata(google = google(), openLibrary = openLibrary())!!
        assertEquals("https://google/cover.jpg", merged.coverUrl)
    }

    @Test
    fun `placeholder google cover is rejected in favour of open library`() {
        val merged = MergeMetadata(
            google = google(),
            openLibrary = openLibrary(),
            coverIsUsable = { it != "https://google/cover.jpg" },
        )!!
        assertEquals("https://ol/cover.jpg", merged.coverUrl)
    }

    @Test
    fun `no cover when every candidate cover is rejected`() {
        val merged = MergeMetadata(
            google = google(),
            openLibrary = openLibrary(),
            coverIsUsable = { false },
        )!!
        assertNull(merged.coverUrl)
    }

    @Test
    fun `missing google cover falls back to open library`() {
        val merged = MergeMetadata(google = google(coverUrl = null), openLibrary = openLibrary())!!
        assertEquals("https://ol/cover.jpg", merged.coverUrl)
    }

    @Test
    fun `isbn comes from whichever source has it`() {
        val merged = MergeMetadata(
            google = google().copy(isbn13 = null),
            openLibrary = openLibrary(),
        )!!
        assertEquals("9780306406157", merged.isbn13)
    }
}
