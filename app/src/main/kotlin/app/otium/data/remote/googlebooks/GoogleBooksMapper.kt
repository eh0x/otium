package app.otium.data.remote.googlebooks

import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource

private const val MAX_CATEGORIES = 5
private const val ISBN_13 = "ISBN_13"
private const val ISBN_10 = "ISBN_10"
private const val YEAR_DIGITS = 4
private val YEAR = Regex("""^\d{$YEAR_DIGITS}""")
private val HTML_BREAK = Regex("""(?i)<br\s*/?>|</p>|</div>|</li>""")
private val HTML_TAG = Regex("""<[^>]*>""")
private val BLANK_RUNS = Regex("""[ \t ]+""")
private val EXTRA_NEWLINES = Regex("""\s*\n\s*""")

/** Volumes without a usable title are dropped: there is nothing to show or save. */
internal fun VolumesResponse.toCandidates(): List<BookCandidate> =
    items.mapNotNull { it.toCandidate() }

internal fun VolumeDto.toCandidate(): BookCandidate? {
    val info = volumeInfo ?: return null
    val title = info.title?.trim().orEmpty()
    if (title.isEmpty()) return null

    return BookCandidate(
        isbn13 = info.isbn(ISBN_13),
        isbn10 = info.isbn(ISBN_10),
        title = title,
        subtitle = info.subtitle.clean(),
        authors = info.authors.map(String::trim).filter(String::isNotEmpty),
        publisher = info.publisher.clean(),
        publishedYear = info.publishedDate?.let { YEAR.find(it.trim())?.value?.toInt() },
        language = info.language.clean(),
        description = info.description?.let(::plainText)?.takeIf(String::isNotEmpty),
        totalPages = info.pageCount?.takeIf { it > 0 },
        categories = info.categories.flatMap { it.split('/') }
            .map(String::trim)
            .filter { it.isNotEmpty() && it != "General" }
            .distinct()
            .take(MAX_CATEGORIES),
        coverUrl = info.imageLinks?.best()?.let(::secureCover),
        source = BookSource.GOOGLE,
    )
}

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun VolumeInfoDto.isbn(type: String): String? =
    industryIdentifiers.firstOrNull { it.type == type }?.identifier.clean()

/** The largest picture Google offers. */
private fun ImageLinksDto.best(): String? =
    listOf(extraLarge, large, medium, small, thumbnail, smallThumbnail).firstNotNullOfOrNull {
        it.clean()
    }

/** Google serves covers over http with a page-curl effect baked in; both are fixed here. */
private fun secureCover(url: String): String? {
    val httpsUrl = url.replaceFirst("http://", "https://").replace("&edge=curl", "")
    return httpsUrl.takeIf { it.startsWith("https://") }
}

/** Google descriptions carry HTML. The app shows plain text, so tags and entities are removed. */
private fun plainText(html: String): String = html
    .replace(HTML_BREAK, "\n")
    .replace(HTML_TAG, " ")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .replace(BLANK_RUNS, " ")
    .replace(EXTRA_NEWLINES, "\n")
    .trim()
