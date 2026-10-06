package app.otium.domain.usecase

import app.otium.domain.error.AppError
import app.otium.domain.error.Try
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateIsbn13Test {

    private fun assertValid(expected: String, input: String) {
        assertEquals(Try.Success(expected), ValidateIsbn13(input))
    }

    private fun assertInvalid(input: String) {
        assertEquals(Try.Failure(AppError.InvalidIsbn(input)), ValidateIsbn13(input))
    }

    @Test
    fun `valid isbn13 is returned normalized`() {
        assertValid("9780306406157", "9780306406157")
        assertValid("9780306406157", "978-0-306-40615-7")
        assertValid("9780306406157", " 978 0 306 40615 7 ")
    }

    @Test
    fun `979 prefix is accepted`() {
        assertValid("9791032305690", "9791032305690")
    }

    @Test
    fun `bad checksum is rejected`() {
        assertInvalid("9780306406158")
    }

    @Test
    fun `non book prefix is rejected even with a valid checksum`() {
        // Valid EAN-13 checksum but not an ISBN prefix.
        assertInvalid("4006381333931")
    }

    @Test
    fun `wrong length and junk are rejected`() {
        assertInvalid("")
        assertInvalid("978030640615")
        assertInvalid("97803064061577")
        assertInvalid("abcdefghijklm")
    }

    @Test
    fun `valid isbn10 is converted to isbn13`() {
        assertValid("9780306406157", "0306406152")
        assertValid("9780306406157", "0-306-40615-2")
    }

    @Test
    fun `isbn10 with x check digit is converted`() {
        // 080442957X -> 9780804429573
        assertValid("9780804429573", "080442957X")
        assertValid("9780804429573", "080442957x")
    }

    @Test
    fun `isbn10 with bad checksum is rejected`() {
        assertInvalid("0306406153")
    }
}
