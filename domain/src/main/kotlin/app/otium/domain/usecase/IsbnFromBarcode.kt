package app.otium.domain.usecase

import app.otium.domain.error.getOrNull

/**
 * Decides whether a scanned barcode is a book. Book barcodes are EAN-13 codes in the "bookland"
 * ranges 978 and 979, and their digits are the ISBN-13 itself. Anything else (a price code, a
 * hyphenated or ISBN-10 string) is not a book read. Returns the ISBN-13, or null.
 */
object IsbnFromBarcode {
    private const val EAN13_LENGTH = 13

    operator fun invoke(raw: String): String? = raw
        .takeIf { it.length == EAN13_LENGTH && it.all { c -> c in '0'..'9' } }
        ?.let { ValidateIsbn13(it).getOrNull() }
}
