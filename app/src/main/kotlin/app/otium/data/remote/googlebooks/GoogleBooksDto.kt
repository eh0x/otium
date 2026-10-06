package app.otium.data.remote.googlebooks

import app.otium.data.remote.LenientInt
import app.otium.data.remote.LenientString
import app.otium.data.remote.LenientStringList
import kotlinx.serialization.Serializable

/** `GET /books/v1/volumes`. `items` is absent, not empty, when nothing matched. */
@Serializable
internal data class VolumesResponse(val items: List<VolumeDto> = emptyList())

@Serializable
internal data class VolumeDto(val volumeInfo: VolumeInfoDto? = null)

/** Only the fields Otium uses. Every one is optional: Google omits whatever it does not know. */
@Serializable
internal data class VolumeInfoDto(
    @Serializable(with = LenientString::class) val title: String? = null,
    @Serializable(with = LenientString::class) val subtitle: String? = null,
    @Serializable(with = LenientStringList::class) val authors: List<String> = emptyList(),
    @Serializable(with = LenientString::class) val publisher: String? = null,
    @Serializable(with = LenientString::class) val publishedDate: String? = null,
    @Serializable(with = LenientString::class) val description: String? = null,
    @Serializable(with = LenientInt::class) val pageCount: Int? = null,
    @Serializable(with = LenientStringList::class) val categories: List<String> = emptyList(),
    @Serializable(with = LenientString::class) val language: String? = null,
    val industryIdentifiers: List<IdentifierDto> = emptyList(),
    val imageLinks: ImageLinksDto? = null,
)

@Serializable
internal data class IdentifierDto(
    @Serializable(with = LenientString::class) val type: String? = null,
    @Serializable(with = LenientString::class) val identifier: String? = null,
)

@Serializable
internal data class ImageLinksDto(
    @Serializable(with = LenientString::class) val smallThumbnail: String? = null,
    @Serializable(with = LenientString::class) val thumbnail: String? = null,
    @Serializable(with = LenientString::class) val small: String? = null,
    @Serializable(with = LenientString::class) val medium: String? = null,
    @Serializable(with = LenientString::class) val large: String? = null,
    @Serializable(with = LenientString::class) val extraLarge: String? = null,
)
