package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try

/**
 * Validates an ISBN and returns it as a normalized ISBN-13 (digits only).
 * Accepts ISBN-13 with a 978/979 prefix and a correct checksum, or a valid ISBN-10,
 * which is converted. Hyphens and whitespace are ignored.
 */
object ValidateIsbn13 {
    private const val ISBN13_LENGTH = 13
    private const val ISBN10_LENGTH = 10
    private const val ISBN13_PAYLOAD_LENGTH = 12
    private const val ISBN10_BODY_LENGTH = 9
    private const val CHECK_MODULUS_13 = 10
    private const val CHECK_MODULUS_10 = 11
    private const val EVEN_WEIGHT = 3
    private const val X_DIGIT_VALUE = 10
    private val PREFIXES = listOf("978", "979")

    operator fun invoke(input: String): Try<String> {
        val cleaned = input.filter { it != '-' && !it.isWhitespace() }.uppercase()
        val isbn13 = when (cleaned.length) {
            ISBN13_LENGTH -> cleaned.takeIf(::isValidIsbn13)
            ISBN10_LENGTH -> isbn10To13(cleaned)
            else -> null
        }
        return if (isbn13 != null) Try.Success(isbn13) else Try.Failure(AppError.InvalidIsbn(input))
    }

    private fun isValidIsbn13(value: String): Boolean = value.all { it in '0'..'9' } &&
        PREFIXES.any(value::startsWith) &&
        checkDigit13(value.take(ISBN13_PAYLOAD_LENGTH)) == value[ISBN13_PAYLOAD_LENGTH]

    private fun checkDigit13(first12: String): Char {
        val sum = first12.mapIndexed { index, c ->
            (c - '0') * if (index % 2 == 0) 1 else EVEN_WEIGHT
        }.sum()
        return '0' + (CHECK_MODULUS_13 - sum % CHECK_MODULUS_13) % CHECK_MODULUS_13
    }

    private fun isbn10To13(isbn10: String): String? {
        val body = isbn10.take(ISBN10_BODY_LENGTH)
        val first12 = PREFIXES.first() + body
        return first12.plus(checkDigit13(first12)).takeIf { isValidIsbn10(isbn10) }
    }

    /** Digits, with an optional X check digit in the last position, weighted 10 down to 1. */
    private fun isValidIsbn10(isbn10: String): Boolean {
        val digits = isbn10.mapIndexed { index, c ->
            when {
                c in '0'..'9' -> c - '0'
                c == 'X' && index == ISBN10_LENGTH - 1 -> X_DIGIT_VALUE
                else -> null
            }
        }
        return digits.none { it == null } &&
            digits.filterNotNull().withIndex().sumOf { (index, digit) ->
                digit * (ISBN10_LENGTH - index)
            } % CHECK_MODULUS_10 == 0
    }
}
