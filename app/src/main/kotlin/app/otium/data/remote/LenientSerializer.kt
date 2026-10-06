package app.otium.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder

/**
 * Reads a field with [inner] and returns [fallback] when the value has the wrong shape, for
 * example a page count that arrives as `"many"` or an author list that arrives as a string. One
 * odd field then costs that field, not the whole book.
 */
internal open class LenientSerializer<T>(
    private val inner: KSerializer<T>,
    private val fallback: T,
) : KSerializer<T> {
    override val descriptor = inner.descriptor

    override fun deserialize(decoder: Decoder): T {
        val jsonDecoder = decoder as? JsonDecoder ?: return inner.deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()
        return try {
            jsonDecoder.json.decodeFromJsonElement(inner, element)
        } catch (ignored: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException: the value had the wrong shape.
            fallback
        }
    }

    override fun serialize(encoder: Encoder, value: T) = inner.serialize(encoder, value)
}

internal object LenientString :
    KSerializer<String?> by LenientSerializer(String.serializer().nullable, null)

internal object LenientInt :
    KSerializer<Int?> by LenientSerializer(Int.serializer().nullable, null)

internal object LenientLong :
    KSerializer<Long?> by LenientSerializer(Long.serializer().nullable, null)

internal object LenientStringList :
    KSerializer<List<String>> by LenientSerializer(ListSerializer(String.serializer()), emptyList())

internal object LenientLongList :
    KSerializer<List<Long>> by LenientSerializer(ListSerializer(Long.serializer()), emptyList())
