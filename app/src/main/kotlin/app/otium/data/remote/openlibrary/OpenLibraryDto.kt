package app.otium.data.remote.openlibrary

import app.otium.data.remote.LenientInt
import app.otium.data.remote.LenientLong
import app.otium.data.remote.LenientLongList
import app.otium.data.remote.LenientSerializer
import app.otium.data.remote.LenientString
import app.otium.data.remote.LenientStringList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement

/** A reference to another record, such as `{"key": "/authors/OL79034A"}`. */
@Serializable
internal data class KeyDto(@Serializable(with = LenientString::class) val key: String? = null)

internal object LenientKeyList :
    KSerializer<List<KeyDto>> by LenientSerializer(ListSerializer(KeyDto.serializer()), emptyList())

/**
 * `GET /isbn/{isbn}.json`: one edition. It names its authors and work only by key, so the names
 * and the description take follow-up requests.
 */
@Serializable
internal data class EditionDto(
    @Serializable(with = LenientString::class) val title: String? = null,
    @Serializable(with = LenientString::class) val subtitle: String? = null,
    @Serializable(with = LenientKeyList::class) val authors: List<KeyDto> = emptyList(),
    @Serializable(with = LenientKeyList::class) val works: List<KeyDto> = emptyList(),
    @Serializable(with = LenientKeyList::class) val languages: List<KeyDto> = emptyList(),
    @Serializable(with = LenientStringList::class) val publishers: List<String> = emptyList(),
    @SerialName("publish_date")
    @Serializable(with = LenientString::class)
    val publishDate: String? = null,
    @SerialName("number_of_pages")
    @Serializable(with = LenientInt::class)
    val numberOfPages: Int? = null,
    @Serializable(with = LenientLongList::class) val covers: List<Long> = emptyList(),
    @SerialName("isbn_10")
    @Serializable(with = LenientStringList::class)
    val isbn10: List<String> = emptyList(),
    @SerialName("isbn_13")
    @Serializable(with = LenientStringList::class)
    val isbn13: List<String> = emptyList(),
    /** A plain string or a `{"type": "/type/text", "value": "..."}` object. */
    val description: JsonElement? = null,
)

/** A work's author entry: `{"author": {"key": "/authors/OL26320A"}}`. */
@Serializable
internal data class WorkAuthorDto(val author: KeyDto? = null)

internal object LenientWorkAuthorList : KSerializer<List<WorkAuthorDto>> by LenientSerializer(
    ListSerializer(WorkAuthorDto.serializer()),
    emptyList(),
)

/**
 * `GET /works/{id}.json`: the part shared by every edition, including the description. Some
 * editions name no authors at all, and then the work is the only place that does.
 */
@Serializable
internal data class WorkDto(
    val description: JsonElement? = null,
    @Serializable(with = LenientStringList::class) val subjects: List<String> = emptyList(),
    @Serializable(with = LenientWorkAuthorList::class)
    val authors: List<WorkAuthorDto> = emptyList(),
)

/** `GET /authors/{id}.json`. */
@Serializable
internal data class AuthorDto(@Serializable(with = LenientString::class) val name: String? = null)

/** `GET /search.json`. */
@Serializable
internal data class SearchResponse(val docs: List<SearchDocDto> = emptyList())

@Serializable
internal data class SearchDocDto(
    @Serializable(with = LenientString::class) val title: String? = null,
    @SerialName("author_name")
    @Serializable(with = LenientStringList::class)
    val authorNames: List<String> = emptyList(),
    @SerialName("first_publish_year")
    @Serializable(with = LenientInt::class)
    val firstPublishYear: Int? = null,
    @Serializable(with = LenientStringList::class) val isbn: List<String> = emptyList(),
    @SerialName("cover_i")
    @Serializable(with = LenientLong::class)
    val coverId: Long? = null,
    @SerialName("number_of_pages_median")
    @Serializable(with = LenientInt::class)
    val pages: Int? = null,
    @Serializable(with = LenientStringList::class) val publisher: List<String> = emptyList(),
    @Serializable(with = LenientStringList::class) val language: List<String> = emptyList(),
    @Serializable(with = LenientStringList::class) val subject: List<String> = emptyList(),
)
