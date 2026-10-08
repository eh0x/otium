package app.otium.data.remote.openlibrary

import app.otium.domain.model.BookCandidate
import app.otium.domain.model.BookSource
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val COVERS_URL = "https://covers.openlibrary.org/b"
private const val MAX_CATEGORIES = 3
private const val ISBN_13_LENGTH = 13
private const val ISBN_10_LENGTH = 10
private val YEAR = Regex("""(?<!\d)(\d{4})(?!\d)""")

/**
 * Open Library names languages with three-letter codes, some of them the bibliographic variants
 * (`fre`, `ger`) that Java does not know. Everything else is derived from the JDK's own table.
 */
private val LANGUAGE_CODES: Map<String, String> = buildMap {
    Locale.getISOLanguages().forEach { put(Locale.forLanguageTag(it).isO3Language, it) }
    putAll(
        mapOf(
            "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh",
            "cze" to "cs", "dut" to "nl", "fre" to "fr", "geo" to "ka", "ger" to "de",
            "gre" to "el", "ice" to "is", "mac" to "mk", "mao" to "mi", "may" to "ms",
            "per" to "fa", "rum" to "ro", "slo" to "sk", "tib" to "bo", "wel" to "cy",
        ),
    )
}

internal fun EditionDto.authorKeys(): List<String> = authors.mapNotNull { it.key }

internal fun EditionDto.workKey(): String? = works.firstNotNullOfOrNull { it.key }

internal fun WorkDto.authorKeys(): List<String> = authors.mapNotNull { it.author?.key }

/** A description is either a plain string or an object with the text under `value`. */
internal fun WorkDto.descriptionText(): String? = description.text()

private fun JsonElement?.text(): String? = when (this) {
    is JsonPrimitive -> if (isString) contentOrNull else null
    is JsonObject -> (this["value"] as? JsonPrimitive)?.contentOrNull
    else -> null
}?.trim()?.takeIf(String::isNotEmpty)

/**
 * Builds a candidate from an edition. [authorNames] and [work] come from follow-up requests and
 * may be missing; the candidate is then simply less complete.
 */
internal fun EditionDto.toCandidate(authorNames: List<String>, work: WorkDto?): BookCandidate {
    val isbn13 = isbn13.firstOrNull { it.isNotBlank() }
    return BookCandidate(
        isbn13 = isbn13,
        isbn10 = isbn10.firstOrNull { it.isNotBlank() },
        title = title.orEmpty().trim(),
        subtitle = subtitle?.trim()?.takeIf(String::isNotEmpty),
        authors = authorNames,
        publisher = publishers.firstOrNull { it.isNotBlank() }?.trim(),
        publishedYear = publishDate?.let { YEAR.find(it)?.groupValues?.get(1)?.toInt() },
        language = languages.firstNotNullOfOrNull { it.key }?.let(::languageCode),
        description = description.text() ?: work?.descriptionText(),
        totalPages = numberOfPages?.takeIf { it > 0 },
        categories = work?.subjects.orEmpty().filter(String::isNotBlank).take(MAX_CATEGORIES),
        coverUrl = covers.firstOrNull { it > 0 }?.let(::coverById) ?: isbn13?.let(::coverByIsbn),
        source = BookSource.OPEN_LIBRARY,
    )
}

internal fun SearchResponse.toCandidates(): List<BookCandidate> = docs.mapNotNull { doc ->
    val title = doc.title?.trim().orEmpty()
    if (title.isEmpty()) return@mapNotNull null
    BookCandidate(
        isbn13 = doc.isbn.firstOrNull { it.length == ISBN_13_LENGTH && it.all(Char::isDigit) },
        isbn10 = doc.isbn.firstOrNull { it.length == ISBN_10_LENGTH },
        title = title,
        authors = doc.authorNames.map(String::trim).filter(String::isNotEmpty),
        publisher = doc.publisher.firstOrNull { it.isNotBlank() }?.trim(),
        publishedYear = doc.firstPublishYear,
        language = doc.language.firstOrNull()?.let(::languageCode),
        totalPages = doc.pages?.takeIf { it > 0 },
        categories = doc.subject.filter(String::isNotBlank).take(MAX_CATEGORIES),
        coverUrl = doc.coverId?.takeIf { it > 0 }?.let(::coverById),
        source = BookSource.OPEN_LIBRARY,
    )
}

private fun coverById(id: Long) = "$COVERS_URL/id/$id-L.jpg"

/** `default=false` makes Open Library answer 404 instead of a blank image when it has no cover. */
private fun coverByIsbn(isbn13: String) = "$COVERS_URL/isbn/$isbn13-L.jpg?default=false"

/** `/languages/eng` or `eng` to `en`. An unknown code is kept as it is rather than guessed. */
private fun languageCode(key: String): String {
    val code = key.substringAfterLast('/')
    return LANGUAGE_CODES[code] ?: code
}
