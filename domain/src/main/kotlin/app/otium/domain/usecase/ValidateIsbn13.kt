package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try

/**
 * Validates an ISBN and returns it as a normalized ISBN-13 (digits only).
 * Accepts ISBN-13 with a 978/979 prefix and a correct checksum, or a valid ISBN-10,
 * which is converted. Hyphens and whitespace are ignored.
 */
object ValidateIsbn13 {
    operator fun invoke(input: String): Try<String> {
        val cleaned = input.filter { it != '-' && !it.isWhitespace() }.uppercase()
        val isbn13 = when (cleaned.length) {
            13 -> cleaned.takeIf(::isValidIsbn13)
            10 -> isbn10To13(cleaned)
            else -> null
        }
        return if (isbn13 != null) Try.Success(isbn13) else Try.Failure(AppError.InvalidIsbn(input))
    }

    private fun isValidIsbn13(value: String): Boolean {
        if (!value.all { it in '0'..'9' }) return false
        if (!value.startsWith("978") && !value.startsWith("979")) return false
        return checkDigit13(value.substring(0, 12)) == value[12]
    }

    private fun checkDigit13(first12: String): Char {
        val sum = first12.mapIndexed { index, c ->
            (c - '0') * if (index % 2 == 0) 1 else 3
        }.sum()
        return '0' + (10 - sum % 10) % 10
    }

    private fun isbn10To13(isbn10: String): String? {
        val body = isbn10.substring(0, 9)
        if (!body.all { it in '0'..'9' }) return null
        val last = isbn10[9]
        if (last != 'X' && last !in '0'..'9') return null

        val sum = isbn10.mapIndexed { index, c ->
            val digit = if (c == 'X') 10 else c - '0'
            digit * (10 - index)
        }.sum()
        if (sum % 11 != 0) return null

        val first12 = "978$body"
        return first12 + checkDigit13(first12)
    }
}
